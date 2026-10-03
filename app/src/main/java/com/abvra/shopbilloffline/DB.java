package com.abvra.shopbilloffline;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class DB extends SQLiteOpenHelper {
    private static final String NAME = "shopbill.db";
    private static final int VERSION = 1;
    public DB(Context c) { super(c, NAME, null, VERSION); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE products(id INTEGER PRIMARY KEY AUTOINCREMENT, product_code TEXT UNIQUE NOT NULL, name TEXT NOT NULL, category TEXT, brand TEXT, unit TEXT, buying_price REAL NOT NULL, selling_price REAL NOT NULL, current_stock REAL NOT NULL DEFAULT 0, min_stock REAL NOT NULL DEFAULT 0, barcode TEXT, tax_rate REAL DEFAULT 0, active INTEGER DEFAULT 1)");
        db.execSQL("CREATE TABLE sales(id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_number TEXT UNIQUE NOT NULL, sale_date INTEGER NOT NULL, subtotal REAL NOT NULL, item_discount REAL NOT NULL, bill_discount REAL NOT NULL, tax REAL NOT NULL, grand_total REAL NOT NULL, payment_method TEXT, status TEXT DEFAULT 'COMPLETED', created_by TEXT)");
        db.execSQL("CREATE TABLE sale_items(id INTEGER PRIMARY KEY AUTOINCREMENT, sale_id INTEGER NOT NULL, product_id INTEGER NOT NULL, product_code TEXT NOT NULL, product_name TEXT NOT NULL, quantity REAL NOT NULL, selling_price_at_sale REAL NOT NULL, buying_price_at_sale REAL NOT NULL, item_discount REAL NOT NULL, tax REAL NOT NULL, line_total REAL NOT NULL)");
        db.execSQL("CREATE TABLE inventory_movements(id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL, movement_type TEXT NOT NULL, quantity REAL NOT NULL, reference_id INTEGER, previous_stock REAL NOT NULL, new_stock REAL NOT NULL, created_at INTEGER NOT NULL, created_by TEXT)");
        db.execSQL("CREATE TABLE audit_log(id INTEGER PRIMARY KEY AUTOINCREMENT, action TEXT NOT NULL, entity_type TEXT, entity_id TEXT, details TEXT, timestamp INTEGER NOT NULL, user_name TEXT)");
        ContentValues c = new ContentValues(); c.put("product_code", "DEMO-001"); c.put("name", "Demo Product"); c.put("category", "Demo"); c.put("unit", "pcs"); c.put("buying_price", 50); c.put("selling_price", 75); c.put("current_stock", 20); c.put("min_stock", 5); db.insert("products", null, c);
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    public Cursor products(String q) {
        String s = "%" + (q == null ? "" : q.trim()) + "%";
        return getReadableDatabase().rawQuery("SELECT * FROM products WHERE active=1 AND (product_code LIKE ? OR name LIKE ? OR barcode LIKE ? OR brand LIKE ?) ORDER BY name", new String[]{s,s,s,s});
    }
    public Cursor allProducts() { return getReadableDatabase().rawQuery("SELECT * FROM products WHERE active=1 ORDER BY name", null); }
    public boolean insertProduct(ContentValues v) { return getWritableDatabase().insert("products", null, v) != -1; }
    public boolean updateProduct(long id, ContentValues v) { return getWritableDatabase().update("products", v, "id=?", new String[]{String.valueOf(id)}) > 0; }
    public void deactivateProduct(long id) { ContentValues v = new ContentValues(); v.put("active", 0); getWritableDatabase().update("products", v, "id=?", new String[]{String.valueOf(id)}); }

    public long completeSale(String invoice, double subtotal, double itemDiscount, double billDiscount, double tax, double grand, String pay, ArrayList<CartItem> items, String user) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction(); long saleId = -1;
        try {
            ContentValues sv = new ContentValues(); sv.put("invoice_number", invoice); sv.put("sale_date", System.currentTimeMillis()); sv.put("subtotal", subtotal); sv.put("item_discount", itemDiscount); sv.put("bill_discount", billDiscount); sv.put("tax", tax); sv.put("grand_total", grand); sv.put("payment_method", pay); sv.put("created_by", user); saleId = db.insertOrThrow("sales", null, sv);
            for (CartItem item : items) {
                Cursor c = db.rawQuery("SELECT buying_price,current_stock FROM products WHERE id=? AND active=1", new String[]{String.valueOf(item.id)});
                if (!c.moveToFirst()) { c.close(); throw new IllegalStateException("Product missing: " + item.name); }
                double buy = c.getDouble(0), stock = c.getDouble(1); c.close();
                if (stock < item.qty) throw new IllegalStateException("Insufficient stock for " + item.name + " (available " + stock + ")");
                double newStock = stock - item.qty;
                ContentValues iv = new ContentValues(); iv.put("sale_id", saleId); iv.put("product_id", item.id); iv.put("product_code", item.code); iv.put("product_name", item.name); iv.put("quantity", item.qty); iv.put("selling_price_at_sale", item.price); iv.put("buying_price_at_sale", buy); iv.put("item_discount", item.discount); iv.put("tax", 0); iv.put("line_total", item.lineTotal()); db.insertOrThrow("sale_items", null, iv);
                ContentValues pv = new ContentValues(); pv.put("current_stock", newStock); db.update("products", pv, "id=?", new String[]{String.valueOf(item.id)});
                ContentValues mv = new ContentValues(); mv.put("product_id", item.id); mv.put("movement_type", "SALE"); mv.put("quantity", -item.qty); mv.put("reference_id", saleId); mv.put("previous_stock", stock); mv.put("new_stock", newStock); mv.put("created_at", System.currentTimeMillis()); mv.put("created_by", user); db.insertOrThrow("inventory_movements", null, mv);
            }
            audit(db, "CREATE_SALE", "SALE", String.valueOf(saleId), invoice, user);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return saleId;
    }
    private void audit(SQLiteDatabase db, String action, String type, String entity, String details, String user) { ContentValues v = new ContentValues(); v.put("action",action); v.put("entity_type",type); v.put("entity_id",entity); v.put("details",details); v.put("timestamp",System.currentTimeMillis()); v.put("user_name",user); db.insert("audit_log",null,v); }
    public Cursor recentSales() { return getReadableDatabase().rawQuery("SELECT invoice_number,sale_date,grand_total,payment_method,status FROM sales ORDER BY sale_date DESC LIMIT 100", null); }
    public Cursor todayStats() { long start = dayStart(); return getReadableDatabase().rawQuery("SELECT COALESCE(SUM(grand_total),0), COUNT(*), COALESCE(SUM(item_discount+bill_discount),0) FROM sales WHERE sale_date>=? AND status='COMPLETED'", new String[]{String.valueOf(start)}); }
    public Cursor todayProfit() { long start = dayStart(); return getReadableDatabase().rawQuery("SELECT COALESCE(SUM(si.line_total),0), COALESCE(SUM(si.buying_price_at_sale*si.quantity),0) FROM sale_items si JOIN sales s ON s.id=si.sale_id WHERE s.sale_date>=? AND s.status='COMPLETED'", new String[]{String.valueOf(start)}); }
    public Cursor allProfit() { return getReadableDatabase().rawQuery("SELECT COALESCE(SUM(si.line_total),0), COALESCE(SUM(si.buying_price_at_sale*si.quantity),0) FROM sale_items si JOIN sales s ON s.id=si.sale_id WHERE s.status='COMPLETED'", null); }
    public Cursor lowStock() { return getReadableDatabase().rawQuery("SELECT product_code,name,current_stock,min_stock FROM products WHERE active=1 AND current_stock<=min_stock ORDER BY current_stock",null); }
    public long dayStart() { String d=new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date()); try { return new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).parse(d+" 00:00").getTime(); } catch(Exception e) { return 0; } }
    public Cursor getSaleItems(long saleId) { return getReadableDatabase().rawQuery("SELECT product_name,quantity,selling_price_at_sale,item_discount,line_total FROM sale_items WHERE sale_id=?", new String[]{String.valueOf(saleId)}); }
    public Cursor saleByInvoice(String invoice) { return getReadableDatabase().rawQuery("SELECT id,grand_total,payment_method,sale_date FROM sales WHERE invoice_number=?", new String[]{invoice}); }
}
