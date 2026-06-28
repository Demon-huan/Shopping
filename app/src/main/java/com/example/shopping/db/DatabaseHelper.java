package com.example.shopping.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.shopping.model.CartItem;
import com.example.shopping.model.Order;
import com.example.shopping.model.OrderItem;
import com.example.shopping.model.PendingOrder;
import com.example.shopping.model.Product;
import com.example.shopping.model.User;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "shopping.db";
    private static final int DATABASE_VERSION = 3;

    // 表名
    private static final String TABLE_USERS = "users";
    private static final String TABLE_PRODUCTS = "products";
    private static final String TABLE_CART = "cart";
    private static final String TABLE_ORDERS = "orders";
    private static final String TABLE_ORDER_ITEMS = "order_items";
    private static final String TABLE_PENDING_ORDERS = "pending_orders";

    // pending_orders 列
    private static final String COL_PENDING_ID = "id";
    private static final String COL_PENDING_USER_ID = "user_id";
    private static final String COL_PENDING_ORDER_JSON = "order_json";
    private static final String COL_PENDING_CREATED_AT = "created_at";

    // users 列
    private static final String COL_USER_ID = "id";
    private static final String COL_USERNAME = "username";
    private static final String COL_PASSWORD = "password";
    private static final String COL_EMAIL = "email";
    private static final String COL_CREATED_AT = "created_at";

    // products 列
    private static final String COL_PRODUCT_ID = "id";
    private static final String COL_NAME = "name";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_PRICE = "price";
    private static final String COL_IMAGE_URL = "image_url";
    private static final String COL_CATEGORY = "category";
    private static final String COL_STOCK = "stock";

    // cart 列
    private static final String COL_CART_ID = "id";
    private static final String COL_USER_ID_FK = "user_id";
    private static final String COL_PRODUCT_ID_FK = "product_id";
    private static final String COL_QUANTITY = "quantity";

    // orders 列
    private static final String COL_ORDER_ID = "id";
    private static final String COL_ORDER_USER_ID = "user_id";
    private static final String COL_TOTAL_PRICE = "total_price";
    private static final String COL_STATUS = "status";
    private static final String COL_ORDER_CREATED_AT = "created_at";

    // order_items 列
    private static final String COL_ORDER_ITEM_ID = "id";
    private static final String COL_ORDER_ID_FK = "order_id";
    private static final String COL_ORDER_PRODUCT_ID = "product_id";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // 首次安装时创建6张表并填充默认商品
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_USERS + " ("
                + COL_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_USERNAME + " TEXT NOT NULL UNIQUE, "
                + COL_PASSWORD + " TEXT NOT NULL, "
                + COL_EMAIL + " TEXT, "
                + COL_CREATED_AT + " TEXT DEFAULT (datetime('now'))"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PRODUCTS + " ("
                + COL_PRODUCT_ID + " INTEGER PRIMARY KEY, "
                + COL_NAME + " TEXT NOT NULL, "
                + COL_DESCRIPTION + " TEXT, "
                + COL_PRICE + " REAL NOT NULL, "
                + COL_IMAGE_URL + " TEXT, "
                + COL_CATEGORY + " TEXT, "
                + COL_STOCK + " INTEGER DEFAULT 100"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_CART + " ("
                + COL_CART_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_USER_ID_FK + " INTEGER NOT NULL, "
                + COL_PRODUCT_ID_FK + " INTEGER NOT NULL, "
                + COL_NAME + " TEXT NOT NULL, "
                + COL_PRICE + " REAL NOT NULL, "
                + COL_QUANTITY + " INTEGER NOT NULL DEFAULT 1, "
                + COL_IMAGE_URL + " TEXT, "
                + "FOREIGN KEY (" + COL_USER_ID_FK + ") REFERENCES " + TABLE_USERS + "(" + COL_USER_ID + "), "
                + "FOREIGN KEY (" + COL_PRODUCT_ID_FK + ") REFERENCES " + TABLE_PRODUCTS + "(" + COL_PRODUCT_ID + ")"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ORDERS + " ("
                + COL_ORDER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_ORDER_USER_ID + " INTEGER NOT NULL, "
                + COL_TOTAL_PRICE + " REAL NOT NULL, "
                + COL_STATUS + " TEXT DEFAULT '已下单', "
                + COL_ORDER_CREATED_AT + " TEXT DEFAULT (datetime('now')), "
                + "FOREIGN KEY (" + COL_ORDER_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + COL_USER_ID + ")"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ORDER_ITEMS + " ("
                + COL_ORDER_ITEM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_ORDER_ID_FK + " INTEGER NOT NULL, "
                + COL_ORDER_PRODUCT_ID + " INTEGER NOT NULL, "
                + COL_NAME + " TEXT NOT NULL, "
                + COL_PRICE + " REAL NOT NULL, "
                + COL_QUANTITY + " INTEGER NOT NULL DEFAULT 1, "
                + "FOREIGN KEY (" + COL_ORDER_ID_FK + ") REFERENCES " + TABLE_ORDERS + "(" + COL_ORDER_ID + ")"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PENDING_ORDERS + " ("
                + COL_PENDING_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_PENDING_USER_ID + " INTEGER NOT NULL, "
                + COL_PENDING_ORDER_JSON + " TEXT NOT NULL, "
                + COL_PENDING_CREATED_AT + " TEXT DEFAULT (datetime('now'))"
                + ")");

        prepopulateProducts(db);
    }

    // 数据库版本升级时，按版本号逐步迁移表结构
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PENDING_ORDERS + " ("
                    + COL_PENDING_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COL_PENDING_USER_ID + " INTEGER NOT NULL, "
                    + COL_PENDING_ORDER_JSON + " TEXT NOT NULL, "
                    + COL_PENDING_CREATED_AT + " TEXT DEFAULT (datetime('now'))"
                    + ")");
        }
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ORDER_ITEMS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ORDERS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_CART);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRODUCTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
            onCreate(db);
        }
    }

    // 写入10条默认商品数据（首次安装时调用）
    private void prepopulateProducts(SQLiteDatabase db) {
        insertProduct(db, 1, "Apple iPhone 15 128GB", "A16仿生芯片 | 4800万像素双摄 | 6.1英寸超视网膜显示屏 | USB-C接口", 5999.00, "https://via.placeholder.com/300.png?text=iPhone15", "电子产品", 50);
        insertProduct(db, 2, "MacBook Pro 14英寸 M3", "Apple M3芯片 | 8GB统一内存 | 512GB SSD | Liquid Retina XDR显示屏", 14999.00, "https://via.placeholder.com/300.png?text=MacBookPro", "电子产品", 30);
        insertProduct(db, 3, "Sony WH-1000XM5 降噪耳机", "行业领先降噪 | 30小时续航 | 快充3分钟播3小时 | 轻量化设计仅250g", 2499.00, "https://via.placeholder.com/300.png?text=SonyXM5", "电子产品", 80);
        insertProduct(db, 4, "Nike Air Force 1 '07", "经典复古鞋型 | Air缓震技术 | 皮革鞋面 | 耐磨橡胶外底", 799.00, "https://via.placeholder.com/300.png?text=NikeAF1", "运动鞋服", 120);
        insertProduct(db, 5, "Adidas 三叶草运动卫衣", "经典三叶草Logo | 纯棉面料 | 宽松版型 | 罗纹袖口设计", 599.00, "https://via.placeholder.com/300.png?text=Adidas", "运动鞋服", 200);
        insertProduct(db, 6, "三只松鼠坚果大礼包 1.5kg", "9种坚果组合 | 每日坚果健康搭配 | 年货送礼佳品 | 独立小包装", 99.00, "https://via.placeholder.com/300.png?text=Nuts", "食品饮料", 500);
        insertProduct(db, 7, "良品铺子 每日坚果 30袋", "科学配比7种坚果果干 | 每袋25g | 独立锁鲜包装 | 30天营养计划", 139.00, "https://via.placeholder.com/300.png?text=Snacks", "食品饮料", 400);
        insertProduct(db, 8, "华为 MatePad Pro 12.6英寸", "麒麟芯片 | OLED全面屏 | 120Hz高刷 | 10050mAh大电池 | 鸿蒙系统", 4299.00, "https://via.placeholder.com/300.png?text=MatePad", "电子产品", 40);
        insertProduct(db, 9, "戴森 V15 无线吸尘器", "激光探测微尘 | 240AW强劲吸力 | 60分钟续航 | LCD实时显示", 4990.00, "https://via.placeholder.com/300.png?text=Dyson", "生活电器", 25);
        insertProduct(db, 10, "小米空气净化器 4 Pro", "CADR值500m³/h | 适用面积60㎡ | OLED触控屏 | 米家APP智控", 1999.00, "https://via.placeholder.com/300.png?text=AirPurifier", "生活电器", 60);
    }

    // 插入单条商品到products表
    private void insertProduct(SQLiteDatabase db, int id, String name, String desc, double price, String imageUrl, String category, int stock) {
        ContentValues values = new ContentValues();
        values.put(COL_PRODUCT_ID, id);
        values.put(COL_NAME, name);
        values.put(COL_DESCRIPTION, desc);
        values.put(COL_PRICE, price);
        values.put(COL_IMAGE_URL, imageUrl);
        values.put(COL_CATEGORY, category);
        values.put(COL_STOCK, stock);
        db.insert(TABLE_PRODUCTS, null, values);
    }

    // ========== 用户操作 ==========

    // 插入新用户，返回-1表示失败
    public long insertUser(User user) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_USERNAME, user.getUsername());
        values.put(COL_PASSWORD, user.getPassword());
        values.put(COL_EMAIL, user.getEmail());
        return db.insert(TABLE_USERS, null, values);
    }

    // 根据用户名查找用户，用于注册时查重
    public User queryUserByUsername(String username) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, COL_USERNAME + "=?", new String[]{username}, null, null, null);
        User user = null;
        if (cursor.moveToFirst()) {
            user = new User();
            user.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_ID)));
            user.setUsername(cursor.getString(cursor.getColumnIndexOrThrow(COL_USERNAME)));
            user.setPassword(cursor.getString(cursor.getColumnIndexOrThrow(COL_PASSWORD)));
            user.setEmail(cursor.getString(cursor.getColumnIndexOrThrow(COL_EMAIL)));
            user.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow(COL_CREATED_AT)));
        }
        cursor.close();
        return user;
    }

    // 同时匹配用户名和密码，用于登录验证
    public User queryUserByUsernameAndPassword(String username, String password) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null,
                COL_USERNAME + "=? AND " + COL_PASSWORD + "=?",
                new String[]{username, password}, null, null, null);
        User user = null;
        if (cursor.moveToFirst()) {
            user = new User();
            user.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_ID)));
            user.setUsername(cursor.getString(cursor.getColumnIndexOrThrow(COL_USERNAME)));
            user.setPassword(cursor.getString(cursor.getColumnIndexOrThrow(COL_PASSWORD)));
            user.setEmail(cursor.getString(cursor.getColumnIndexOrThrow(COL_EMAIL)));
            user.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow(COL_CREATED_AT)));
        }
        cursor.close();
        return user;
    }

    // ========== 商品操作 ==========

    // 查询所有商品，按ID升序排列
    public List<Product> getAllProducts() {
        List<Product> products = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_PRODUCTS, null, null, null, null, null, COL_PRODUCT_ID + " ASC");
        while (cursor.moveToNext()) {
            Product p = new Product();
            p.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_PRODUCT_ID)));
            p.setName(cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)));
            p.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRIPTION)));
            p.setPrice(cursor.getDouble(cursor.getColumnIndexOrThrow(COL_PRICE)));
            p.setImageUrl(cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_URL)));
            p.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY)));
            p.setStock(cursor.getInt(cursor.getColumnIndexOrThrow(COL_STOCK)));
            products.add(p);
        }
        cursor.close();
        return products;
    }

    // 根据商品ID查询单个商品
    public Product getProductById(int id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_PRODUCTS, null, COL_PRODUCT_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        Product p = null;
        if (cursor.moveToFirst()) {
            p = new Product();
            p.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_PRODUCT_ID)));
            p.setName(cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)));
            p.setDescription(cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRIPTION)));
            p.setPrice(cursor.getDouble(cursor.getColumnIndexOrThrow(COL_PRICE)));
            p.setImageUrl(cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_URL)));
            p.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY)));
            p.setStock(cursor.getInt(cursor.getColumnIndexOrThrow(COL_STOCK)));
        }
        cursor.close();
        return p;
    }

    // 插入或更新商品，ID相同时覆盖旧数据
    public void insertOrUpdateProduct(Product product) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PRODUCT_ID, product.getId());
        values.put(COL_NAME, product.getName());
        values.put(COL_DESCRIPTION, product.getDescription());
        values.put(COL_PRICE, product.getPrice());
        values.put(COL_IMAGE_URL, product.getImageUrl());
        values.put(COL_CATEGORY, product.getCategory());
        values.put(COL_STOCK, product.getStock());
        db.insertWithOnConflict(TABLE_PRODUCTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    // ========== 购物车操作 ==========

    // 新增一条购物车记录
    public long insertCartItem(CartItem item) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_USER_ID_FK, item.getUserId());
        values.put(COL_PRODUCT_ID_FK, item.getProductId());
        values.put(COL_NAME, item.getName());
        values.put(COL_PRICE, item.getPrice());
        values.put(COL_QUANTITY, item.getQuantity());
        values.put(COL_IMAGE_URL, item.getImageUrl());
        return db.insert(TABLE_CART, null, values);
    }

    // 查询某个用户购物车里的所有商品
    public List<CartItem> getCartItemsByUserId(int userId) {
        List<CartItem> items = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_CART, null, COL_USER_ID_FK + "=?", new String[]{String.valueOf(userId)}, null, null, COL_CART_ID + " DESC");
        while (cursor.moveToNext()) {
            CartItem item = new CartItem();
            item.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_CART_ID)));
            item.setUserId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_ID_FK)));
            item.setProductId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_PRODUCT_ID_FK)));
            item.setName(cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)));
            item.setPrice(cursor.getDouble(cursor.getColumnIndexOrThrow(COL_PRICE)));
            item.setQuantity(cursor.getInt(cursor.getColumnIndexOrThrow(COL_QUANTITY)));
            item.setImageUrl(cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_URL)));
            items.add(item);
        }
        cursor.close();
        return items;
    }

    // 根据用户ID和商品ID查购物车，用于加入购物车时判断是否已存在
    public CartItem getCartItemByProductId(int userId, int productId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_CART, null,
                COL_USER_ID_FK + "=? AND " + COL_PRODUCT_ID_FK + "=?",
                new String[]{String.valueOf(userId), String.valueOf(productId)},
                null, null, null);
        CartItem item = null;
        if (cursor.moveToFirst()) {
            item = new CartItem();
            item.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_CART_ID)));
            item.setUserId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_USER_ID_FK)));
            item.setProductId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_PRODUCT_ID_FK)));
            item.setName(cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)));
            item.setPrice(cursor.getDouble(cursor.getColumnIndexOrThrow(COL_PRICE)));
            item.setQuantity(cursor.getInt(cursor.getColumnIndexOrThrow(COL_QUANTITY)));
            item.setImageUrl(cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_URL)));
        }
        cursor.close();
        return item;
    }

    // 更新购物车中某个商品的数量
    public void updateCartItemQuantity(int id, int quantity) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_QUANTITY, quantity);
        db.update(TABLE_CART, values, COL_CART_ID + "=?", new String[]{String.valueOf(id)});
    }

    // 删除购物车中的单个商品
    public int deleteCartItem(int id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_CART, COL_CART_ID + "=?", new String[]{String.valueOf(id)});
    }

    // 清空某个用户的全部购物车商品
    public void clearCart(int userId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_CART, COL_USER_ID_FK + "=?", new String[]{String.valueOf(userId)});
    }

    // ========== 订单操作 ==========

    // 创建本地订单，不清空购物车
    public long createOrderOnly(int userId, double totalPrice, List<CartItem> cartItems, String createdAt, String status) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues orderValues = new ContentValues();
            orderValues.put(COL_ORDER_USER_ID, userId);
            orderValues.put(COL_TOTAL_PRICE, totalPrice);
            orderValues.put(COL_STATUS, status != null ? status : "已下单");
            if (createdAt != null) {
                orderValues.put(COL_ORDER_CREATED_AT, createdAt);
            }
            long orderId = db.insert(TABLE_ORDERS, null, orderValues);

            for (CartItem item : cartItems) {
                ContentValues itemValues = new ContentValues();
                itemValues.put(COL_ORDER_ID_FK, orderId);
                itemValues.put(COL_ORDER_PRODUCT_ID, item.getProductId());
                itemValues.put(COL_NAME, item.getName());
                itemValues.put(COL_PRICE, item.getPrice());
                itemValues.put(COL_QUANTITY, item.getQuantity());
                db.insert(TABLE_ORDER_ITEMS, null, itemValues);
            }

            db.setTransactionSuccessful();
            return orderId;
        } finally {
            db.endTransaction();
        }
    }

    // 缓存一条未同步成功的订单JSON
    public long insertPendingOrder(int userId, String orderJson) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_PENDING_USER_ID, userId);
        values.put(COL_PENDING_ORDER_JSON, orderJson);
        return db.insert(TABLE_PENDING_ORDERS, null, values);
    }

    // 查询所有待同步的订单
    public List<PendingOrder> getAllPendingOrders() {
        List<PendingOrder> pendingOrders = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_PENDING_ORDERS, null,
                null, null, null, null, COL_PENDING_ID + " ASC");
        while (cursor.moveToNext()) {
            PendingOrder po = new PendingOrder();
            po.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_PENDING_ID)));
            po.setUserId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_PENDING_USER_ID)));
            po.setOrderJson(cursor.getString(cursor.getColumnIndexOrThrow(COL_PENDING_ORDER_JSON)));
            po.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow(COL_PENDING_CREATED_AT)));
            pendingOrders.add(po);
        }
        cursor.close();
        return pendingOrders;
    }

    // 同步成功后删除对应的待处理记录
    public void deletePendingOrder(int pendingOrderId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_PENDING_ORDERS,
                COL_PENDING_ID + "=?",
                new String[]{String.valueOf(pendingOrderId)});
    }

    // 删除订单及其所有子项，事务保证一致性
    public void deleteOrder(int orderId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_ORDER_ITEMS, COL_ORDER_ID_FK + "=?", new String[]{String.valueOf(orderId)});
            db.delete(TABLE_ORDERS, COL_ORDER_ID + "=?", new String[]{String.valueOf(orderId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // 同步成功后把"待同步"改成"已下单"
    public void updateOrderStatus(int orderId, String status) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_STATUS, status);
        db.update(TABLE_ORDERS, values, COL_ORDER_ID + "=?", new String[]{String.valueOf(orderId)});
    }

    // 用远端数据覆盖本地，保证两边一致
    public void syncOrdersFromRemote(int userId, List<Order> remoteOrders) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // 先清掉旧的本地订单
            Cursor cursor = db.query(TABLE_ORDERS, new String[]{COL_ORDER_ID},
                    COL_ORDER_USER_ID + "=?", new String[]{String.valueOf(userId)},
                    null, null, null);
            while (cursor.moveToNext()) {
                int orderId = cursor.getInt(0);
                db.delete(TABLE_ORDER_ITEMS, COL_ORDER_ID_FK + "=?", new String[]{String.valueOf(orderId)});
            }
            cursor.close();
            db.delete(TABLE_ORDERS, COL_ORDER_USER_ID + "=?", new String[]{String.valueOf(userId)});

            // 批量写入远端订单，ID沿用MockAPI的
            for (Order order : remoteOrders) {
                ContentValues values = new ContentValues();
                values.put(COL_ORDER_ID, order.getId());
                values.put(COL_ORDER_USER_ID, order.getUserId());
                values.put(COL_TOTAL_PRICE, order.getTotalPrice());
                values.put(COL_STATUS, order.getStatus());
                values.put(COL_ORDER_CREATED_AT, order.getCreatedAt());
                db.insert(TABLE_ORDERS, null, values);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // 查询某个用户的所有订单，按ID倒序
    public List<Order> getOrdersByUserId(int userId) {
        List<Order> orders = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_ORDERS, null,
                COL_ORDER_USER_ID + "=?",
                new String[]{String.valueOf(userId)},
                null, null, COL_ORDER_ID + " DESC");
        while (cursor.moveToNext()) {
            Order order = new Order();
            order.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_ORDER_ID)));
            order.setUserId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_ORDER_USER_ID)));
            order.setTotalPrice(cursor.getDouble(cursor.getColumnIndexOrThrow(COL_TOTAL_PRICE)));
            order.setStatus(cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS)));
            order.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow(COL_ORDER_CREATED_AT)));
            orders.add(order);
        }
        cursor.close();
        return orders;
    }

    // 查询某个订单下的所有商品明细
    public List<OrderItem> getOrderItemsByOrderId(int orderId) {
        List<OrderItem> items = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_ORDER_ITEMS, null,
                COL_ORDER_ID_FK + "=?",
                new String[]{String.valueOf(orderId)},
                null, null, COL_ORDER_ITEM_ID + " ASC");
        while (cursor.moveToNext()) {
            OrderItem item = new OrderItem();
            item.setId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_ORDER_ITEM_ID)));
            item.setOrderId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_ORDER_ID_FK)));
            item.setProductId(cursor.getInt(cursor.getColumnIndexOrThrow(COL_ORDER_PRODUCT_ID)));
            item.setName(cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)));
            item.setPrice(cursor.getDouble(cursor.getColumnIndexOrThrow(COL_PRICE)));
            item.setQuantity(cursor.getInt(cursor.getColumnIndexOrThrow(COL_QUANTITY)));
            items.add(item);
        }
        cursor.close();
        return items;
    }
}
