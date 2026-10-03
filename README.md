# ShopBill Offline

Offline-first Android shop billing and inventory MVP.

## Implemented
- Local SQLite product catalogue
- Manual product entry/edit/delete
- CSV import with validation/preview summary
- Billing cart with quantity and per-item percentage discount
- Bill-level fixed discount
- Cash / UPI / Card / Other payment method
- Automatic stock deduction
- Historical buying cost stored per sale item
- Admin-only cost/profit dashboard protected by PIN
- Shop receipt settings
- Invoice history and reprint
- Bluetooth paired-device thermal printing using ESC/POS-style bytes
- Local database backup/restore through Android document picker
- Low-stock highlighting

## Default admin PIN
`1234`

Change it from Admin > Settings.

## CSV columns
`product_code,product_name,category,brand,unit,buying_price,selling_price,opening_stock,minimum_stock,barcode,tax_rate`

CSV import uses the Android file picker and does not require network access.

## Printer
Pair the thermal printer in Android Bluetooth settings first. Then use Admin > Printer to select a paired device and test print.
