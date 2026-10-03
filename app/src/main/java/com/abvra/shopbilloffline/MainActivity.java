package com.abvra.shopbilloffline;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    DB db;
    SharedPreferences prefs;
    LinearLayout root;
    LinearLayout body;
    TextView title;

    String mode = "BILLING";
    ArrayList<CartItem> cart = new ArrayList<>();

    int blue = 0xff1a73e8;
    int dark = 0xff202124;
    int bg = 0xfff6f8fb;
    int green = 0xff188038;
    int red = 0xffd93025;

    final int CSV_REQ = 9001;
    final int BACKUP_REQ = 9002;
    final int RESTORE_REQ = 9003;
    final int BT_REQ = 9004;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        db = new DB(this);
        prefs = getSharedPreferences("shop", 0);

        buildShell();
        showBilling();
    }

    TextView tv(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(dark);
        t.setPadding(16, 12, 16, 12);
        return t;
    }

    Button btn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setPadding(14, 8, 14, 8);
        e.setBackgroundColor(0xffffffff);
        return e;
    }

    TextView heading(String s) {
        TextView h = tv(s, 20);
        h.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        h.setPadding(8, 12, 8, 8);
        return h;
    }

    void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(6, 4, 6, 4);
        top.setBackgroundColor(0xffffffff);

        title = tv("ShopBill Offline", 22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        top.addView(
                title,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        Button admin = btn("Admin");
        admin.setOnClickListener(v -> adminGate());

        top.addView(
                admin,
                new LinearLayout.LayoutParams(-2, -2)
        );

        root.addView(top);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        ScrollView sc = new ScrollView(this);
        sc.addView(body);

        root.addView(
                sc,
                new LinearLayout.LayoutParams(-1, 0, 1)
        );

        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(4, 4, 4, 4);
        nav.setBackgroundColor(0xffffffff);

        Button bill = btn("Bill");
        Button products = btn("Products");
        Button history = btn("History");

        bill.setOnClickListener(v -> showBilling());
        products.setOnClickListener(v -> adminGate());
        history.setOnClickListener(v -> showHistory());

        nav.addView(
                bill,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        nav.addView(
                products,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        nav.addView(
                history,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        root.addView(nav);

        setContentView(root);
    }

    int ColorUtil(int c) {
        return c;
    }

    void clear(String t) {
        body.removeAllViews();
        title.setText(t);
    }

    // =========================================================
    // BILLING
    // =========================================================

    void showBilling() {

        mode = "BILLING";
        clear("New Bill");

        EditText search = edit("Search product / code / barcode");

        body.addView(
                search,
                new LinearLayout.LayoutParams(-1, -2)
        );

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);

        body.addView(
                results,
                new LinearLayout.LayoutParams(-1, -2)
        );

        search.addTextChangedListener(
                new SimpleTextWatcher() {
                    public void afterTextChanged(android.text.Editable e) {
                        fillResults(results, e.toString());
                    }
                }
        );

        fillResults(results, "");

        body.addView(heading("Cart"));

        LinearLayout cartBox = new LinearLayout(this);
        cartBox.setOrientation(LinearLayout.VERTICAL);

        body.addView(
                cartBox,
                new LinearLayout.LayoutParams(-1, -2)
        );

        EditText billDiscount = edit("Bill discount ₹");

        billDiscount.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        body.addView(
                billDiscount,
                new LinearLayout.LayoutParams(-1, -2)
        );

        Spinner pay = new Spinner(this);

        String[] pays = {
                "Cash",
                "UPI",
                "Card",
                "Other"
        };

        pay.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        pays
                )
        );

        body.addView(
                pay,
                new LinearLayout.LayoutParams(-1, -2)
        );

        TextView totals = heading("Total: ₹0.00");
        body.addView(totals);

        Button checkout = btn("Complete Sale & Print");

        body.addView(
                checkout,
                new LinearLayout.LayoutParams(-1, -2)
        );

        /*
         * FIX:
         *
         * The previous code declared:
         *
         * Runnable refresh = () -> {
         *     ...
         *     refresh.run();
         * };
         *
         * Java does not allow a local variable to reference itself
         * during its own initialization.
         *
         * Therefore we use a one-element Runnable array.
         */
        final Runnable[] refresh = new Runnable[1];

        refresh[0] = () -> {

            cartBox.removeAllViews();

            double subtotal = 0;

            for (CartItem c : cart) {

                subtotal += c.lineTotal();

                cartBox.addView(
                        cartRow(
                                c,
                                () -> refresh[0].run()
                        )
                );
            }

            double bd = parse(
                    billDiscount
                            .getText()
                            .toString()
            );

            double grand = Math.max(
                    0,
                    subtotal - bd
            );

            totals.setText(
                    "Subtotal: " +
                            money(subtotal) +
                            "\nBill discount: " +
                            money(bd) +
                            "\nTOTAL: " +
                            money(grand)
            );
        };

        billDiscount.setOnFocusChangeListener(
                (v, f) -> {
                    if (!f) {
                        refresh[0].run();
                    }
                }
        );

        checkout.setOnClickListener(v -> {

            refresh[0].run();

            double sub = cartTotal();

            double bd = parse(
                    billDiscount
                            .getText()
                            .toString()
            );

            if (cart.isEmpty()) {
                toast("Cart is empty");
                return;
            }

            double grand = Math.max(
                    0,
                    sub - bd
            );

            if (bd > sub) {
                toast(
                        "Bill discount cannot exceed subtotal"
                );
                return;
            }

            completeBill(
                    grand,
                    bd,
                    pay.getSelectedItem().toString()
            );
        });

        refresh[0].run();
    }

    void fillResults(
            LinearLayout results,
            String q
    ) {

        results.removeAllViews();

        Cursor c = db.products(q);

        int n = 0;

        while (c.moveToNext() && n < 20) {

            long id =
                    c.getLong(
                            c.getColumnIndexOrThrow("id")
                    );

            String code =
                    c.getString(
                            c.getColumnIndexOrThrow("product_code")
                    );

            String name =
                    c.getString(
                            c.getColumnIndexOrThrow("name")
                    );

            double price =
                    c.getDouble(
                            c.getColumnIndexOrThrow("selling_price")
                    );

            double stock =
                    c.getDouble(
                            c.getColumnIndexOrThrow("current_stock")
                    );

            Button b = btn(
                    name +
                            "  •  " +
                            code +
                            "  •  ₹" +
                            money(price) +
                            "  •  Stock " +
                            fmt(stock)
            );

            b.setOnClickListener(v -> {

                if (stock <= 0) {
                    toast("Out of stock");
                    return;
                }

                addCart(
                        id,
                        code,
                        name,
                        price
                );

                toast("Added " + name);
            });

            results.addView(b);

            n++;
        }

        c.close();
    }

    void addCart(
            long id,
            String code,
            String name,
            double p
    ) {

        for (CartItem x : cart) {

            if (x.id == id) {
                x.qty++;
                return;
            }
        }

        cart.add(
                new CartItem(
                        id,
                        code,
                        name,
                        p
                )
        );
    }

    View cartRow(
            CartItem c,
            Runnable refresh
    ) {

        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.VERTICAL
        );

        row.setPadding(
                8,
                8,
                8,
                8
        );

        TextView nm =
                tv(
                        c.name +
                                "  •  ₹" +
                                money(c.price),
                        16
                );

        nm.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        row.addView(nm);

        LinearLayout controls =
                new LinearLayout(this);

        EditText qty =
                edit("Qty");

        qty.setText(
                fmt(c.qty)
        );

        qty.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        EditText disc =
                edit("Item discount %");

        disc.setText(
                c.discount == 0
                        ? ""
                        : fmt(c.discount)
        );

        disc.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        Button minus = btn("−");
        Button plus = btn("+");
        Button del = btn("Remove");

        minus.setOnClickListener(v -> {

            c.qty =
                    Math.max(
                            1,
                            c.qty - 1
                    );

            qty.setText(
                    fmt(c.qty)
            );

            refresh.run();
        });

        plus.setOnClickListener(v -> {

            c.qty++;

            qty.setText(
                    fmt(c.qty)
            );

            refresh.run();
        });

        del.setOnClickListener(v -> {

            cart.remove(c);

            refresh.run();
        });

        qty.setOnFocusChangeListener(
                (v, f) -> {

                    if (!f) {

                        c.qty =
                                Math.max(
                                        1,
                                        parse(
                                                qty
                                                        .getText()
                                                        .toString()
                                        )
                                );

                        refresh.run();
                    }
                }
        );

        disc.setOnFocusChangeListener(
                (v, f) -> {

                    if (!f) {

                        c.discount =
                                Math.max(
                                        0,
                                        Math.min(
                                                100,
                                                parse(
                                                        disc
                                                                .getText()
                                                                .toString()
                                                )
                                        )
                                );

                        refresh.run();
                    }
                }
        );

        controls.addView(
                minus,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        0.6f
                )
        );

        controls.addView(
                qty,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1f
                )
        );

        controls.addView(
                plus,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        0.6f
                )
        );

        controls.addView(
                disc,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1.4f
                )
        );

        controls.addView(
                del,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1.0f
                )
        );

        row.addView(controls);

        row.addView(
                tv(
                        "Line total: " +
                                money(c.lineTotal()),
                        14
                )
        );

        return row;
    }

    double cartTotal() {

        double x = 0;

        for (CartItem c : cart) {
            x += c.lineTotal();
        }

        return x;
    }

    double cartItemDiscount() {

        double x = 0;

        for (CartItem c : cart) {
            x += c.lineDiscount();
        }

        return x;
    }

    void completeBill(
            double grand,
            double billDisc,
            String pay
    ) {

        String inv = nextInvoice();

        try {

            long id =
                    db.completeSale(
                            inv,
                            cartTotal(),
                            cartItemDiscount(),
                            billDisc,
                            0,
                            grand,
                            pay,
                            cart,
                            "CASHIER"
                    );

            String receipt =
                    receiptForSale(
                            id,
                            inv,
                            grand,
                            pay
                    );

            new AlertDialog.Builder(this)
                    .setTitle("Sale Complete")
                    .setMessage(receipt)
                    .setPositiveButton(
                            "Print",
                            (d, w) ->
                                    printReceipt(receipt)
                    )
                    .setNegativeButton(
                            "Done",
                            null
                    )
                    .show();

            cart.clear();

            showBilling();

        } catch (Exception e) {

            toast(
                    e.getMessage()
            );
        }
    }

    String nextInvoice() {

        long x =
                prefs.getLong(
                        "invoice_seq",
                        0
                ) + 1;

        prefs.edit()
                .putLong(
                        "invoice_seq",
                        x
                )
                .apply();

        return prefs.getString(
                "invoice_prefix",
                "INV"
        ) +
                "-" +
                String.format(
                        "%06d",
                        x
                );
    }

    String shop(
            String key,
            String def
    ) {

        return prefs.getString(
                key,
                def
        );
    }

    String money(double d) {

        return String.format(
                java.util.Locale.US,
                "%.2f",
                d
        );
    }

    String fmt(double d) {

        return d % 1 == 0
                ? String.format(
                        java.util.Locale.US,
                        "%.0f",
                        d
                )
                : money(d);
    }

    double parse(String s) {

        try {

            return Double.parseDouble(
                    s.trim()
            );

        } catch (Exception e) {

            return 0;
        }
    }

    // =========================================================
    // PRODUCTS
    // =========================================================

    void showProducts() {

        mode = "PRODUCTS";

        clear(
                "Products — Admin"
        );

        Button add =
                btn("Add Product");

        Button csv =
                btn("Import CSV");

        LinearLayout tools =
                new LinearLayout(this);

        tools.addView(
                add,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        tools.addView(
                csv,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        body.addView(tools);

        body.addView(
                tv(
                        "Long-press a product to edit or deactivate. " +
                                "Buying price is visible only here.",
                        14
                )
        );

        EditText s =
                edit("Search products");

        body.addView(s);

        LinearLayout list =
                new LinearLayout(this);

        list.setOrientation(
                LinearLayout.VERTICAL
        );

        body.addView(list);

        add.setOnClickListener(
                v -> productDialog(null)
        );

        csv.setOnClickListener(
                v -> openCsv()
        );

        s.addTextChangedListener(
                new SimpleTextWatcher() {

                    public void afterTextChanged(
                            android.text.Editable e
                    ) {

                        fillProductList(
                                list,
                                e.toString()
                        );
                    }
                }
        );

        fillProductList(
                list,
                ""
        );
    }

    void fillProductList(
            LinearLayout list,
            String q
    ) {

        list.removeAllViews();

        Cursor c =
                db.products(q);

        while (c.moveToNext()) {

            long id =
                    c.getLong(0);

            String code =
                    c.getString(1);

            String name =
                    c.getString(2);

            double buy =
                    c.getDouble(6);

            double sell =
                    c.getDouble(7);

            double stock =
                    c.getDouble(8);

            double min =
                    c.getDouble(9);

            TextView t =
                    tv(
                            code +
                                    " | " +
                                    name +
                                    "\nBuy ₹" +
                                    money(buy) +
                                    "  Sell ₹" +
                                    money(sell) +
                                    "  Stock " +
                                    fmt(stock) +
                                    (
                                            stock <= min
                                                    ? "  • LOW STOCK"
                                                    : ""
                                    ),
                            15
                    );

            if (stock <= min) {
                t.setTextColor(red);
            }

            t.setBackgroundColor(
                    0xffffffff
            );

            t.setOnLongClickListener(
                    v -> {

                        new AlertDialog.Builder(this)
                                .setTitle(name)
                                .setItems(
                                        new String[]{
                                                "Edit",
                                                "Deactivate"
                                        },
                                        (d, w) -> {

                                            if (w == 0) {

                                                productDialog(id);

                                            } else {

                                                db.deactivateProduct(id);

                                                fillProductList(
                                                        list,
                                                        q
                                                );
                                            }
                                        }
                                )
                                .show();

                        return true;
                    }
            );

            list.addView(t);
        }

        c.close();
    }

    void productDialog(Long id) {

        String code = "";
        String name = "";
        String cat = "";
        String brand = "";
        String unit = "pcs";
        String buy = "";
        String sell = "";
        String stock = "";
        String min = "";
        String barcode = "";
        String tax = "0";

        if (id != null) {

            Cursor c =
                    db.getReadableDatabase()
                            .rawQuery(
                                    "SELECT * FROM products WHERE id=?",
                                    new String[]{
                                            String.valueOf(id)
                                    }
                            );

            if (c.moveToFirst()) {

                code = c.getString(1);
                name = c.getString(2);
                cat = c.getString(3);
                brand = c.getString(4);
                unit = c.getString(5);
                buy = money(c.getDouble(6));
                sell = money(c.getDouble(7));
                stock = fmt(c.getDouble(8));
                min = fmt(c.getDouble(9));

                barcode =
                        c.getString(10) == null
                                ? ""
                                : c.getString(10);

                tax =
                        money(c.getDouble(11));
            }

            c.close();
        }

        LinearLayout f =
                new LinearLayout(this);

        f.setOrientation(
                LinearLayout.VERTICAL
        );

        String[] hints = {
                "Product code *",
                "Name *",
                "Category",
                "Brand",
                "Unit",
                "Buying price *",
                "Selling price *",
                "Stock *",
                "Minimum stock",
                "Barcode",
                "Tax %"
        };

        EditText[] e =
                new EditText[hints.length];

        for (int i = 0; i < hints.length; i++) {

            e[i] =
                    edit(hints[i]);

            f.addView(e[i]);
        }

        e[0].setText(code);
        e[1].setText(name);
        e[2].setText(cat);
        e[3].setText(brand);
        e[4].setText(unit);
        e[5].setText(buy);
        e[6].setText(sell);
        e[7].setText(stock);
        e[8].setText(min);
        e[9].setText(barcode);
        e[10].setText(tax);

        ScrollView sc =
                new ScrollView(this);

        sc.addView(f);

        AlertDialog dlg =
                new AlertDialog.Builder(this)
                        .setTitle(
                                id == null
                                        ? "Add Product"
                                        : "Edit Product"
                        )
                        .setView(sc)
                        .setNegativeButton(
                                "Cancel",
                                null
                        )
                        .setPositiveButton(
                                "Save",
                                null
                        )
                        .create();

        dlg.setOnShowListener(
                x -> {

                    dlg.getButton(-1)
                            .setOnClickListener(
                                    v -> {

                                        if (
                                                e[0]
                                                        .getText()
                                                        .toString()
                                                        .trim()
                                                        .isEmpty()
                                                        ||
                                                e[1]
                                                        .getText()
                                                        .toString()
                                                        .trim()
                                                        .isEmpty()
                                        ) {

                                            toast(
                                                    "Code and name are required"
                                            );

                                            return;
                                        }

                                        ContentValues cv =
                                                new ContentValues();

                                        cv.put(
                                                "product_code",
                                                e[0]
                                                        .getText()
                                                        .toString()
                                                        .trim()
                                        );

                                        cv.put(
                                                "name",
                                                e[1]
                                                        .getText()
                                                        .toString()
                                                        .trim()
                                        );

                                        cv.put(
                                                "category",
                                                e[2]
                                                        .getText()
                                                        .toString()
                                        );

                                        cv.put(
                                                "brand",
                                                e[3]
                                                        .getText()
                                                        .toString()
                                        );

                                        cv.put(
                                                "unit",
                                                e[4]
                                                        .getText()
                                                        .toString()
                                        );

                                        cv.put(
                                                "buying_price",
                                                parse(
                                                        e[5]
                                                                .getText()
                                                                .toString()
                                                )
                                        );

                                        cv.put(
                                                "selling_price",
                                                parse(
                                                        e[6]
                                                                .getText()
                                                                .toString()
                                                )
                                        );

                                        cv.put(
                                                "current_stock",
                                                parse(
                                                        e[7]
                                                                .getText()
                                                                .toString()
                                                )
                                        );

                                        cv.put(
                                                "min_stock",
                                                parse(
                                                        e[8]
                                                                .getText()
                                                                .toString()
                                                )
                                        );

                                        cv.put(
                                                "barcode",
                                                e[9]
                                                        .getText()
                                                        .toString()
                                        );

                                        cv.put(
                                                "tax_rate",
                                                parse(
                                                        e[10]
                                                                .getText()
                                                                .toString()
                                                )
                                        );

                                        try {

                                            boolean ok =
                                                    id == null
                                                            ? db.insertProduct(cv)
                                                            : db.updateProduct(
                                                                    id,
                                                                    cv
                                                            );

                                            if (!ok) {
                                                throw new Exception(
                                                        "Save failed"
                                                );
                                            }

                                            dlg.dismiss();

                                            showProducts();

                                        } catch (
                                                Exception ex
                                        ) {

                                            toast(
                                                    "Could not save: " +
                                                            ex.getMessage()
                                            );
                                        }
                                    }
                            );
                }
        );

        dlg.show();
    }

    // =========================================================
    // HISTORY
    // =========================================================

    void showHistory() {

        clear(
                "Invoice History"
        );

        Cursor c =
                db.recentSales();

        while (c.moveToNext()) {

            String inv =
                    c.getString(0);

            double total =
                    c.getDouble(2);

            String pay =
                    c.getString(3);

            String status =
                    c.getString(4);

            Button b =
                    btn(
                            inv +
                                    "  •  ₹" +
                                    money(total) +
                                    "  •  " +
                                    pay +
                                    "  •  " +
                                    status
                    );

            b.setOnClickListener(
                    v -> showInvoice(inv)
            );

            body.addView(b);
        }

        c.close();
    }

    void showInvoice(String inv) {

        Cursor s =
                db.saleByInvoice(inv);

        if (!s.moveToFirst()) {

            toast(
                    "Invoice not found"
            );

            return;
        }

        long sid =
                s.getLong(0);

        double total =
                s.getDouble(1);

        String pay =
                s.getString(2);

        String r =
                receiptForSale(
                        sid,
                        inv,
                        total,
                        pay
                );

        new AlertDialog.Builder(this)
                .setTitle(inv)
                .setMessage(r)
                .setPositiveButton(
                        "Reprint",
                        (d, w) ->
                                printReceipt(r)
                )
                .setNegativeButton(
                        "Close",
                        null
                )
                .show();

        s.close();
    }

    String receiptForSale(
            long sid,
            String inv,
            double total,
            String pay
    ) {

        StringBuilder r =
                new StringBuilder();

        r.append(
                "==============================\n"
        );

        r.append(
                shop(
                        "shop_name",
                        "MY SHOP"
                )
        );

        r.append("\n");

        r.append(
                shop(
                        "shop_address",
                        "Shop address"
                )
        );

        r.append("\n");

        r.append(
                shop(
                        "shop_phone",
                        "Phone"
                )
        );

        r.append("\n");

        r.append(
                "Invoice: "
        );

        r.append(inv);

        r.append("\n");

        Cursor c =
                db.getSaleItems(sid);

        while (c.moveToNext()) {

            r.append(
                    c.getString(0)
            );

            r.append("  x");

            r.append(
                    fmt(c.getDouble(1))
            );

            r.append("  ₹");

            r.append(
                    money(
                            c.getDouble(4)
                    )
            );

            r.append("\n");
        }

        c.close();

        r.append(
                "------------------------------\n"
        );

        r.append(
                "TOTAL: ₹"
        );

        r.append(
                money(total)
        );

        r.append("\n");

        r.append(
                "Payment: "
        );

        r.append(pay);

        r.append("\n");

        r.append(
                shop(
                        "footer",
                        "Thank you! Visit again."
                )
        );

        r.append("\n");

        r.append(
                "=============================="
        );

        return r.toString();
    }

    // =========================================================
    // ADMIN
    // =========================================================

    void adminGate() {

        EditText pin =
                edit("Admin PIN");

        pin.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                InputType.TYPE_NUMBER_VARIATION_PASSWORD
        );

        new AlertDialog.Builder(this)
                .setTitle(
                        "Admin Login"
                )
                .setView(pin)
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Enter",
                        (d, w) -> {

                            if (
                                    pin
                                            .getText()
                                            .toString()
                                            .equals(
                                                    prefs.getString(
                                                            "admin_pin",
                                                            "1234"
                                                    )
                                            )
                            ) {

                                showAdmin();

                            } else {

                                toast(
                                        "Wrong PIN"
                                );
                            }
                        }
                )
                .show();
    }

    void showAdmin() {

        clear(
                "Admin Dashboard"
        );

        Cursor st =
                db.todayStats();

        double sales = 0;
        double disc = 0;
        int tx = 0;

        if (st.moveToFirst()) {

            sales =
                    st.getDouble(0);

            tx =
                    st.getInt(1);

            disc =
                    st.getDouble(2);
        }

        st.close();

        Cursor pf =
                db.todayProfit();

        double saleLines = 0;
        double cost = 0;

        if (pf.moveToFirst()) {

            saleLines =
                    pf.getDouble(0);

            cost =
                    pf.getDouble(1);
        }

        pf.close();

        body.addView(
                heading("Today")
        );

        body.addView(
                tv(
                        "Sales: ₹" +
                                money(sales) +
                                "\nTransactions: " +
                                tx +
                                "\nDiscounts: ₹" +
                                money(disc) +
                                "\nEstimated Gross Profit: ₹" +
                                money(
                                        saleLines - cost
                                ),
                        18
                )
        );

        Cursor lp =
                db.lowStock();

        int low = 0;

        while (lp.moveToNext()) {
            low++;
        }

        lp.close();

        body.addView(
                tv(
                        "Low-stock products: " +
                                low,
                        16
                )
        );

        Button products =
                btn("Manage Products / CSV");

        Button reports =
                btn("Profit & Sales Report");

        Button shop =
                btn("Shop Settings");

        Button printer =
                btn("Bluetooth Printer");

        Button backup =
                btn("Backup Database");

        Button restore =
                btn("Restore Database");

        Button pin =
                btn("Change Admin PIN");

        body.addView(products);
        body.addView(reports);
        body.addView(shop);
        body.addView(printer);
        body.addView(backup);
        body.addView(restore);
        body.addView(pin);

        products.setOnClickListener(
                v -> showProducts()
        );

        reports.setOnClickListener(
                v -> showReports()
        );

        shop.setOnClickListener(
                v -> shopSettings()
        );

        printer.setOnClickListener(
                v -> printerDialog()
        );

        backup.setOnClickListener(
                v -> createBackup()
        );

        restore.setOnClickListener(
                v -> chooseRestore()
        );

        pin.setOnClickListener(
                v -> changePin()
        );
    }

    void showReports() {

        clear(
                "Sales & Profit"
        );

        Cursor a =
                db.allProfit();

        double rev = 0;
        double cost = 0;

        if (a.moveToFirst()) {

            rev =
                    a.getDouble(0);

            cost =
                    a.getDouble(1);
        }

        a.close();

        body.addView(
                heading(
                        "All-time completed sales"
                )
        );

        body.addView(
                tv(
                        "Line revenue: ₹" +
                                money(rev) +
                                "\nCost of goods sold: ₹" +
                                money(cost) +
                                "\nGross profit: ₹" +
                                money(
                                        rev - cost
                                ) +
                                "\nGross margin: " +
                                (
                                        rev > 0
                                                ? money(
                                                (rev - cost)
                                                        / rev
                                                        * 100
                                        )
                                                : "0.00"
                                ) +
                                "%",
                        20
                )
        );
    }

    void shopSettings() {

        LinearLayout f =
                new LinearLayout(this);

        f.setOrientation(
                LinearLayout.VERTICAL
        );

        EditText n =
                edit("Shop name");

        n.setText(
                shop(
                        "shop_name",
                        "MY SHOP"
                )
        );

        EditText a =
                edit("Address");

        a.setText(
                shop(
                        "shop_address",
                        ""
                )
        );

        EditText p =
                edit("Phone");

        p.setText(
                shop(
                        "shop_phone",
                        ""
                )
        );

        EditText g =
                edit("GSTIN (optional)");

        g.setText(
                shop(
                        "gstin",
                        ""
                )
        );

        EditText pref =
                edit("Invoice prefix");

        pref.setText(
                shop(
                        "invoice_prefix",
                        "INV"
                )
        );

        EditText foot =
                edit("Receipt footer");

        foot.setText(
                shop(
                        "footer",
                        "Thank you! Visit again."
                )
        );

        f.addView(n);
        f.addView(a);
        f.addView(p);
        f.addView(g);
        f.addView(pref);
        f.addView(foot);

        new AlertDialog.Builder(this)
                .setTitle(
                        "Shop Settings"
                )
                .setView(f)
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Save",
                        (d, w) -> {

                            prefs.edit()
                                    .putString(
                                            "shop_name",
                                            n.getText().toString()
                                    )
                                    .putString(
                                            "shop_address",
                                            a.getText().toString()
                                    )
                                    .putString(
                                            "shop_phone",
                                            p.getText().toString()
                                    )
                                    .putString(
                                            "gstin",
                                            g.getText().toString()
                                    )
                                    .putString(
                                            "invoice_prefix",
                                            pref.getText().toString()
                                    )
                                    .putString(
                                            "footer",
                                            foot.getText().toString()
                                    )
                                    .apply();

                            toast(
                                    "Saved"
                            );
                        }
                )
                .show();
    }

    void changePin() {

        LinearLayout f =
                new LinearLayout(this);

        f.setOrientation(
                LinearLayout.VERTICAL
        );

        EditText o =
                edit("Current PIN");

        o.setInputType(
                2 | 16
        );

        EditText n =
                edit("New PIN");

        n.setInputType(
                2 | 16
        );

        f.addView(o);
        f.addView(n);

        new AlertDialog.Builder(this)
                .setTitle(
                        "Change Admin PIN"
                )
                .setView(f)
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Save",
                        (d, w) -> {

                            if (
                                    !o.getText()
                                            .toString()
                                            .equals(
                                                    prefs.getString(
                                                            "admin_pin",
                                                            "1234"
                                                    )
                                            )
                            ) {

                                toast(
                                        "Current PIN incorrect"
                                );

                                return;
                            }

                            if (
                                    n.getText()
                                            .toString()
                                            .length() < 4
                            ) {

                                toast(
                                        "Use at least 4 digits"
                                );

                                return;
                            }

                            prefs.edit()
                                    .putString(
                                            "admin_pin",
                                            n.getText().toString()
                                    )
                                    .apply();

                            toast(
                                    "PIN changed"
                            );
                        }
                )
                .show();
    }

    // =========================================================
    // CSV / BACKUP / RESTORE
    // =========================================================

    void openCsv() {

        Intent i =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                );

        i.setType("text/*");

        i.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        startActivityForResult(
                i,
                CSV_REQ
        );
    }

    void createBackup() {

        Intent i =
                new Intent(
                        Intent.ACTION_CREATE_DOCUMENT
                );

        i.setType(
                "application/octet-stream"
        );

        i.putExtra(
                Intent.EXTRA_TITLE,
                "shopbill-backup.db"
        );

        startActivityForResult(
                i,
                BACKUP_REQ
        );
    }

    void chooseRestore() {

        Intent i =
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                );

        i.setType(
                "application/octet-stream"
        );

        i.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        startActivityForResult(
                i,
                RESTORE_REQ
        );
    }

    @Override
    protected void onActivityResult(
            int req,
            int res,
            Intent data
    ) {

        super.onActivityResult(
                req,
                res,
                data
        );

        if (
                res != RESULT_OK ||
                data == null
        ) {
            return;
        }

        try {

            if (req == CSV_REQ) {

                importCsv(
                        data.getData()
                );

            } else if (
                    req == BACKUP_REQ
            ) {

                copyDbTo(
                        data.getData()
                );

            } else if (
                    req == RESTORE_REQ
            ) {

                restoreDbFrom(
                        data.getData()
                );
            }

        } catch (Exception e) {

            toast(
                    "Operation failed: " +
                            e.getMessage()
            );
        }
    }

    void importCsv(Uri uri)
            throws Exception {

        ContentResolver cr =
                getContentResolver();

        BufferedReader br =
                new BufferedReader(
                        new InputStreamReader(
                                cr.openInputStream(uri),
                                StandardCharsets.UTF_8
                        )
                );

        String line;

        int count = 0;
        int err = 0;

        boolean first = true;

        SQLiteDatabase w =
                db.getWritableDatabase();

        w.beginTransaction();

        try {

            while (
                    (line = br.readLine()) != null
            ) {

                if (first) {

                    first = false;
                    continue;
                }

                String[] a =
                        parseCsv(line);

                if (a.length < 8) {

                    err++;
                    continue;
                }

                try {

                    ContentValues v =
                            new ContentValues();

                    v.put(
                            "product_code",
                            a[0].trim()
                    );

                    v.put(
                            "name",
                            a[1].trim()
                    );

                    v.put(
                            "category",
                            a.length > 2
                                    ? a[2].trim()
                                    : ""
                    );

                    v.put(
                            "brand",
                            a.length > 3
                                    ? a[3].trim()
                                    : ""
                    );

                    v.put(
                            "unit",
                            a.length > 4
                                    ? a[4].trim()
                                    : "pcs"
                    );

                    v.put(
                            "buying_price",
                            parse(a[5])
                    );

                    v.put(
                            "selling_price",
                            parse(a[6])
                    );

                    v.put(
                            "current_stock",
                            parse(a[7])
                    );

                    v.put(
                            "min_stock",
                            a.length > 8
                                    ? parse(a[8])
                                    : 0
                    );

                    v.put(
                            "barcode",
                            a.length > 9
                                    ? a[9].trim()
                                    : ""
                    );

                    v.put(
                            "tax_rate",
                            a.length > 10
                                    ? parse(a[10])
                                    : 0
                    );

                    long id =
                            w.insertWithOnConflict(
                                    "products",
                                    null,
                                    v,
                                    SQLiteDatabase.CONFLICT_IGNORE
                            );

                    if (id == -1) {

                        w.update(
                                "products",
                                v,
                                "product_code=?",
                                new String[]{
                                        a[0].trim()
                                }
                        );
                    }

                    count++;

                } catch (Exception x) {

                    err++;
                }
            }

            w.setTransactionSuccessful();

        } finally {

            w.endTransaction();

            br.close();
        }

        toast(
                "CSV imported: " +
                        count +
                        " records, errors: " +
                        err
        );

        showProducts();
    }

    String[] parseCsv(
            String line
    ) {

        ArrayList<String> out =
                new ArrayList<>();

        StringBuilder s =
                new StringBuilder();

        boolean q = false;

        for (
                int i = 0;
                i < line.length();
                i++
        ) {

            char ch =
                    line.charAt(i);

            if (ch == '"') {

                q = !q;
                continue;
            }

            if (
                    ch == ',' &&
                    !q
            ) {

                out.add(
                        s.toString()
                );

                s.setLength(0);

            } else {

                s.append(ch);
            }
        }

        out.add(
                s.toString()
        );

        return out.toArray(
                new String[0]
        );
    }

    void copyDbTo(Uri uri)
            throws Exception {

        db.close();

        try (
                InputStream in =
                        new java.io.FileInputStream(
                                getDatabasePath(
                                        "shopbill.db"
                                )
                        );

                OutputStream out =
                        getContentResolver()
                                .openOutputStream(uri)
        ) {

            byte[] b =
                    new byte[8192];

            int n;

            while (
                    (n = in.read(b)) > 0
            ) {

                out.write(
                        b,
                        0,
                        n
                );
            }
        }

        db =
                new DB(this);

        toast(
                "Backup saved"
        );
    }

    void restoreDbFrom(Uri uri)
            throws Exception {

        db.close();

        try (
                InputStream in =
                        getContentResolver()
                                .openInputStream(uri);

                OutputStream out =
                        new java.io.FileOutputStream(
                                getDatabasePath(
                                        "shopbill.db"
                                )
                        )
        ) {

            byte[] b =
                    new byte[8192];

            int n;

            while (
                    (n = in.read(b)) > 0
            ) {

                out.write(
                        b,
                        0,
                        n
                );
            }
        }

        db =
                new DB(this);

        toast(
                "Database restored. Restart the app if a screen looks stale."
        );

        showAdmin();
    }

    // =========================================================
    // BLUETOOTH PRINTER
    // =========================================================

    void printerDialog() {

        if (
                Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(
                        Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                    },
                    BT_REQ
            );

            return;
        }

        BluetoothAdapter a =
                BluetoothAdapter.getDefaultAdapter();

        if (a == null) {

            toast(
                    "Bluetooth not available"
            );

            return;
        }

        Set<BluetoothDevice> dev =
                a.getBondedDevices();

        ArrayList<BluetoothDevice> list =
                new ArrayList<>(dev);

        String[] names =
                new String[list.size()];

        for (
                int i = 0;
                i < list.size();
                i++
        ) {

            names[i] =
                    list.get(i).getName() +
                            "\n" +
                            list.get(i).getAddress();
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        "Paired Bluetooth Printers"
                )
                .setItems(
                        names,
                        (d, w) -> {

                            prefs.edit()
                                    .putString(
                                            "printer_addr",
                                            list.get(w)
                                                    .getAddress()
                                    )
                                    .apply();

                            testPrint(
                                    list.get(w)
                            );
                        }
                )
                .setNegativeButton(
                        "Open Bluetooth Settings",
                        (d, w) ->
                                startActivity(
                                        new Intent(
                                                Settings.ACTION_BLUETOOTH_SETTINGS
                                        )
                                )
                )
                .show();
    }

    void testPrint(
            BluetoothDevice device
    ) {

        String test =
                "\n" +
                        shop(
                                "shop_name",
                                "MY SHOP"
                        ) +
                        "\nBluetooth printer test" +
                        "\n--------------------------" +
                        "\nOK\n\n\n";

        sendBluetooth(
                device,
                test
        );
    }

    void printReceipt(
            String receipt
    ) {

        String addr =
                prefs.getString(
                        "printer_addr",
                        ""
                );

        if (
                addr.isEmpty()
        ) {

            printerDialog();
            return;
        }

        if (
                Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(
                        Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT
                    },
                    BT_REQ
            );

            return;
        }

        BluetoothAdapter a =
                BluetoothAdapter.getDefaultAdapter();

        BluetoothDevice d =
                a.getRemoteDevice(addr);

        sendBluetooth(
                d,
                receipt +
                        "\n\n\n"
        );
    }

    void sendBluetooth(
            BluetoothDevice device,
            String text
    ) {

        new Thread(
                () -> {

                    try {

                        UUID uuid =
                                UUID.fromString(
                                        "00001101-0000-1000-8000-00805F9B34FB"
                                );

                        BluetoothSocket s =
                                device.createRfcommSocketToServiceRecord(
                                        uuid
                                );

                        s.connect();

                        OutputStream o =
                                s.getOutputStream();

                        ByteArrayOutputStream b =
                                new ByteArrayOutputStream();

                        b.write(
                                new byte[]{
                                        0x1B,
                                        0x40
                                }
                        );

                        b.write(
                                text.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        );

                        b.write(
                                new byte[]{
                                        0x0A,
                                        0x0A,
                                        0x0A
                                }
                        );

                        o.write(
                                b.toByteArray()
                        );

                        o.flush();

                        s.close();

                        runOnUiThread(
                                () ->
                                        toast(
                                                "Printed"
                                        )
                        );

                    } catch (
                            Exception e
                    ) {

                        runOnUiThread(
                                () ->
                                        toast(
                                                "Printer error: " +
                                                        e.getMessage()
                                        )
                        );
                    }
                }
        ).start();
    }

    // =========================================================
    // UTILITIES
    // =========================================================

    void toast(String s) {

        Toast.makeText(
                this,
                s,
                Toast.LENGTH_LONG
        ).show();
    }

    abstract static class SimpleTextWatcher
            implements android.text.TextWatcher {

        public void beforeTextChanged(
                CharSequence s,
                int st,
                int c,
                int a
        ) {
        }

        public void onTextChanged(
                CharSequence s,
                int st,
                int b,
                int c
        ) {
        }
    }
}
