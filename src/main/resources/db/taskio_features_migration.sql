/*
   Taskio feature expansion for an existing Taskio database.
   Run this once in SQL Server Management Studio after selecting the Taskio DB.
   Safe to rerun: tables/columns/constraints are guarded by existence checks.
*/
USE Taskio;
GO

IF OBJECT_ID('dbo.customers', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.customers (
        id INT IDENTITY(1,1) PRIMARY KEY,
        full_name NVARCHAR(120) NOT NULL,
        phone NVARCHAR(20) NULL,
        email NVARCHAR(160) NULL,
        address NVARCHAR(300) NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_customers_created_at DEFAULT SYSUTCDATETIME()
    );
    CREATE UNIQUE INDEX UX_customers_phone_not_null ON dbo.customers(phone) WHERE phone IS NOT NULL;
END;
GO

IF COL_LENGTH('dbo.orders', 'status') IS NULL
    ALTER TABLE dbo.orders ADD status NVARCHAR(30) NOT NULL CONSTRAINT DF_orders_status DEFAULT 'COMPLETED' WITH VALUES;
IF COL_LENGTH('dbo.orders', 'customer_id') IS NULL
    ALTER TABLE dbo.orders ADD customer_id INT NULL;
IF COL_LENGTH('dbo.orders', 'subtotal_amount') IS NULL
    ALTER TABLE dbo.orders ADD subtotal_amount DECIMAL(18,2) NULL;
IF COL_LENGTH('dbo.orders', 'discount_amount') IS NULL
    ALTER TABLE dbo.orders ADD discount_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_orders_discount_amount DEFAULT 0 WITH VALUES;
IF COL_LENGTH('dbo.orders', 'promotion_id') IS NULL
    ALTER TABLE dbo.orders ADD promotion_id INT NULL;
GO

IF OBJECT_ID('dbo.promotions', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.promotions (
        id INT IDENTITY(1,1) PRIMARY KEY,
        code NVARCHAR(40) NOT NULL UNIQUE,
        name NVARCHAR(120) NOT NULL,
        description NVARCHAR(500) NULL,
        discount_type NVARCHAR(12) NOT NULL,
        discount_value DECIMAL(18,2) NOT NULL,
        minimum_order_amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_promotions_min_order DEFAULT 0,
        starts_at DATETIME2 NULL,
        ends_at DATETIME2 NULL,
        usage_limit INT NULL,
        usage_count INT NOT NULL CONSTRAINT DF_promotions_usage_count DEFAULT 0,
        active BIT NOT NULL CONSTRAINT DF_promotions_active DEFAULT 1,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_promotions_created_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_promotions_discount_type CHECK (discount_type IN ('PERCENT', 'AMOUNT')),
        CONSTRAINT CK_promotions_discount_value CHECK (discount_value > 0)
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_orders_customers')
    ALTER TABLE dbo.orders ADD CONSTRAINT FK_orders_customers FOREIGN KEY (customer_id) REFERENCES dbo.customers(id);
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_orders_promotions')
    ALTER TABLE dbo.orders ADD CONSTRAINT FK_orders_promotions FOREIGN KEY (promotion_id) REFERENCES dbo.promotions(id);
GO

IF OBJECT_ID('dbo.inventory_transactions', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.inventory_transactions (
        id INT IDENTITY(1,1) PRIMARY KEY,
        product_id INT NOT NULL,
        user_id INT NULL,
        transaction_type NVARCHAR(24) NOT NULL,
        quantity INT NOT NULL,
        unit_cost DECIMAL(18,2) NULL,
        reference_code NVARCHAR(50) NULL,
        note NVARCHAR(500) NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_inventory_transactions_created_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT CK_inventory_transactions_type CHECK (transaction_type IN ('RECEIPT', 'SUPPLIER_RETURN', 'CUSTOMER_RETURN', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT')),
        CONSTRAINT CK_inventory_transactions_quantity CHECK (quantity > 0),
        CONSTRAINT FK_inventory_transactions_products FOREIGN KEY (product_id) REFERENCES dbo.products(id),
        CONSTRAINT FK_inventory_transactions_users FOREIGN KEY (user_id) REFERENCES dbo.users(id)
    );
END;
GO

IF OBJECT_ID('dbo.sales_returns', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.sales_returns (
        id INT IDENTITY(1,1) PRIMARY KEY,
        order_id INT NOT NULL,
        user_id INT NULL,
        reason NVARCHAR(500) NOT NULL,
        status NVARCHAR(20) NOT NULL CONSTRAINT DF_sales_returns_status DEFAULT 'ACCEPTED',
        refund_amount DECIMAL(18,2) NOT NULL,
        created_at DATETIME2 NOT NULL CONSTRAINT DF_sales_returns_created_at DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_sales_returns_orders FOREIGN KEY (order_id) REFERENCES dbo.orders(id),
        CONSTRAINT FK_sales_returns_users FOREIGN KEY (user_id) REFERENCES dbo.users(id)
    );
END;
IF OBJECT_ID('dbo.sales_return_details', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.sales_return_details (
        id INT IDENTITY(1,1) PRIMARY KEY,
        return_id INT NOT NULL,
        product_id INT NOT NULL,
        quantity INT NOT NULL,
        unit_price DECIMAL(18,2) NOT NULL,
        CONSTRAINT CK_sales_return_details_quantity CHECK (quantity > 0),
        CONSTRAINT FK_sales_return_details_returns FOREIGN KEY (return_id) REFERENCES dbo.sales_returns(id),
        CONSTRAINT FK_sales_return_details_products FOREIGN KEY (product_id) REFERENCES dbo.products(id)
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_orders_status_created_date' AND object_id = OBJECT_ID('dbo.orders'))
    CREATE INDEX IX_orders_status_created_date ON dbo.orders(status, created_date DESC);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_inventory_transactions_created_at' AND object_id = OBJECT_ID('dbo.inventory_transactions'))
    CREATE INDEX IX_inventory_transactions_created_at ON dbo.inventory_transactions(created_at DESC);
GO

/* Existing invoices predate subtotal_amount; initialize it without changing totals. */
UPDATE dbo.orders SET subtotal_amount = total_amount WHERE subtotal_amount IS NULL;
GO
