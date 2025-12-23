## In-app Rewind 2025 (HTML-only)

File: `inapp-rewind-2025.html`

### Mở để xem nhanh (local)

```bash
python3 -m http.server 8080 --directory /workspace
```

Sau đó mở `http://localhost:8080/inapp-rewind-2025.html`.

### Bơm dữ liệu thật (in-app)

Trang sẽ đọc dữ liệu từ `window.__REWIND_DATA__` (nếu có). Ví dụ:

```html
<script>
  window.__REWIND_DATA__ = {
    year: "2025",
    storeName: "Bún Bò Cô Ba",
    province: "TP.HCM",
    totalProducts: 58234,
    bestProductName: "Bún bò đặc biệt",
    bestProductOrders: 10234,
    bestProductShare: 17.6,
    topCampaignName: "Mưa deal ngập tràn",
    topCampaignOrders: 3210,
    topCampaignLift: 2.8,
    peakStart: "11",
    peakEnd: "13",
    awardTitle: "Top Cửa hàng nổi bật",
    rank: "3",
  };
</script>
```

Chỉ cần đảm bảo đoạn script trên chạy **trước khi** `inapp-rewind-2025.html` render (tuỳ cách bạn nhúng vào webview/in-app).
