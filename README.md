## In-app Rewind 2025 (Vertical scroll, Liquid)

File: `inapp-rewind-2025.html`

### Mở để xem nhanh (local)

```bash
python3 -m http.server 8080 --directory /workspace
```

Sau đó mở `http://localhost:8080/inapp-rewind-2025.html`.

### Liquid variables

Trang này **chỉ dùng HTML/CSS** (không JavaScript), optimized cho mobile in-app.

- `merchant_name`
- `item_quantity`
- `top1item_name`
- `time`
- `month`
- `campaign_name` (nếu rỗng/không có sẽ hiện block “Đối tác không tham gia chiến dịch”)
- `order_percent`
