## In-app Rewind 2025 (HTML-only)

File: `inapp-rewind-2025.html`

### Mở để xem nhanh (local)

```bash
python3 -m http.server 8080 --directory /workspace
```

Sau đó mở `http://localhost:8080/inapp-rewind-2025.html`.

### Liquid template variables (in-app)

File này **không dùng JavaScript** và tương thích Liquid. Bạn chỉ cần render các biến sau:

- `year` (string) – ví dụ: `2025`
- `store_name` (string)
- `province` (string)
- `total_products` (number/string)
- `avg_per_day` (number/string, tuỳ bạn tính trước)
- `best_product_name` (string)
- `best_product_orders` (number/string)
- `best_product_share` (number/string, %)
- `top_campaign_name` (string)
- `top_campaign_orders` (number/string)
- `top_campaign_lift` (number/string, x)
- `peak_start` (string, giờ dạng `11`)
- `peak_end` (string, giờ dạng `13`)
- `award_title` (string)
- `rank` (string/number)
