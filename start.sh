#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "  TruyenGG Spring Boot - Startup Script"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if Docker is running
echo -e "${YELLOW}[1/5]${NC} Kiểm tra Docker..."
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}✗ Docker không chạy. Vui lòng khởi động Docker Desktop trước.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Docker đang chạy${NC}"

# Check if PostgreSQL container exists and is running
echo -e "${YELLOW}[2/5]${NC} Kiểm tra PostgreSQL container..."
if docker ps -a | grep -q truyengg-postgres; then
    if docker ps | grep -q truyengg-postgres; then
        echo -e "${GREEN}✓ PostgreSQL container đang chạy${NC}"
    else
        echo -e "${YELLOW}→ Khởi động PostgreSQL container...${NC}"
        docker start truyengg-postgres > /dev/null 2>&1 || docker-compose up -d postgres
    fi
else
    echo -e "${YELLOW}→ Tạo PostgreSQL container...${NC}"
    docker-compose up -d postgres
fi

# Wait for PostgreSQL to be ready
echo -e "${YELLOW}[3/5]${NC} Đợi PostgreSQL sẵn sàng..."
MAX_RETRIES=30
RETRY_COUNT=0
while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    if pg_isready -h localhost -p 5432 -U postgres > /dev/null 2>&1; then
        echo -e "${GREEN}✓ PostgreSQL sẵn sàng${NC}"
        break
    fi
    RETRY_COUNT=$((RETRY_COUNT + 1))
    echo -n "."
    sleep 1
done
echo ""

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
    echo -e "${RED}✗ PostgreSQL không khởi động được sau ${MAX_RETRIES} giây${NC}"
    exit 1
fi

# Set environment variables
export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo "")
if [ -z "$JAVA_HOME" ]; then
    echo -e "${RED}✗ Không tìm thấy Java 21. Vui lòng cài đặt Java 21.${NC}"
    exit 1
fi

export DB_USERNAME=${DB_USERNAME:-postgres}
export DB_PASSWORD=${DB_PASSWORD:-postgres}
export JWT_SECRET=${JWT_SECRET:-your-secret-key-change-in-production-min-256-bits}

echo -e "${GREEN}✓ Java 21: $JAVA_HOME${NC}"
echo ""

# Check if application is already running
if lsof -ti:8080 > /dev/null 2>&1; then
    echo -e "${YELLOW}[4/5]${NC} Ứng dụng đang chạy trên port 8080"
    read -p "Bạn có muốn dừng và khởi động lại? (y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}→ Dừng ứng dụng cũ...${NC}"
        pkill -f "bootRun" 2>/dev/null || true
        lsof -ti:8080 | xargs kill -9 2>/dev/null || true
        sleep 2
    else
        echo "Giữ nguyên ứng dụng đang chạy."
        exit 0
    fi
fi

# Start the application
echo -e "${YELLOW}[5/5]${NC} Khởi động ứng dụng Spring Boot..."
echo -e "${YELLOW}→ Logs sẽ được ghi vào app.log${NC}"
echo ""

# Clean old log
> app.log

# Start application in background
nohup ./gradlew bootRun --no-daemon > app.log 2>&1 &
APP_PID=$!

echo "Ứng dụng đang khởi động với PID: $APP_PID"
echo "Đợi ứng dụng khởi động (có thể mất 30-60 giây)..."
echo ""

# Wait for application to start
MAX_WAIT=60
WAIT_COUNT=0
STARTED=false

while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
    # Check if process is still running
    if ! ps -p $APP_PID > /dev/null 2>&1; then
        echo -e "${RED}✗ Ứng dụng đã dừng. Kiểm tra logs:${NC}"
        tail -30 app.log
        exit 1
    fi
    
    # Check if application is responding
    if curl -s http://localhost:8080/ > /dev/null 2>&1; then
        STARTED=true
        break
    fi
    
    # Check logs for startup message
    if grep -q "Started TruyenGgApplication" app.log 2>/dev/null; then
        STARTED=true
        break
    fi
    
    # Check for errors
    if grep -q "APPLICATION FAILED TO START" app.log 2>/dev/null; then
        echo -e "${RED}✗ Ứng dụng khởi động thất bại. Logs:${NC}"
        tail -50 app.log | grep -A 20 "APPLICATION FAILED"
        kill $APP_PID 2>/dev/null || true
        exit 1
    fi
    
    WAIT_COUNT=$((WAIT_COUNT + 1))
    echo -n "."
    sleep 1
done
echo ""

if [ "$STARTED" = true ]; then
    echo ""
    echo -e "${GREEN}=========================================="
    echo -e "  ✓ Ứng dụng đã khởi động thành công!"
    echo -e "==========================================${NC}"
    echo ""
    echo "🌐 Truy cập ứng dụng tại:"
    echo "   http://localhost:8080"
    echo ""
    echo "📊 API Documentation:"
    echo "   http://localhost:8080/swagger-ui.html"
    echo ""
    echo "📝 Xem logs:"
    echo "   tail -f app.log"
    echo ""
    echo "🛑 Dừng ứng dụng:"
    echo "   pkill -f bootRun"
    echo "   hoặc: kill $APP_PID"
    echo ""
    
    # Run page check
    echo -e "${YELLOW}Kiểm tra các trang...${NC}"
    sleep 2
    ./check-pages.sh
else
    echo -e "${RED}✗ Ứng dụng không khởi động được sau ${MAX_WAIT} giây${NC}"
    echo "Kiểm tra logs:"
    tail -50 app.log
    kill $APP_PID 2>/dev/null || true
    exit 1
fi
