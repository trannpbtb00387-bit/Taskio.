# SOF3041 / Taskio

Taskio is a Spring Boot + Thymeleaf sales and inventory management coursework app. Java packages use `com.poly` (without the `.sof3041` suffix). The application includes public introduction, registration/login, dashboard, product CRUD, POS checkout, order workflow, inventory receipts/returns, customers/loyal customers, promotions, and reports.

## Import into Spring Tool Suite

1. In Spring Tool Suite, select **File → Import → Maven → Existing Maven Projects**.
2. Select this `SOF3041-Taskio` folder and the `pom.xml` file.
3. Finish the import and allow Maven to download dependencies.
4. Open `src/main/resources/application.properties` and set your SQL Server URL, username, and password. The defaults can also be overridden with `TASKIO_DB_URL`, `TASKIO_DB_USERNAME`, and `TASKIO_DB_PASSWORD`.
5. In SQL Server Management Studio, connect to the `Taskio` database and run `src/main/resources/db/taskio_features_migration.sql` once. This extends an existing four-table database without dropping its existing data.
6. Run `Sof3041Application.java` as a Spring Boot App.

## Main routes

- Public introduction: `/` or `/about`; login and registration: `/login`, `/register`.
- Internal pages: `/dashboard`, `/orders`, `/products`, `/pos`, `/inventory`, `/customers`, `/promotions`, `/reports`.
- Order queues: `/orders/process/waiting-confirmation`, `/orders/process/processing`, `/orders/process/ready`.
- A completed POS sale reduces stock in the same database transaction. Promotions are checked and applied during checkout. A customer can optionally be attached to the sale.
- Staff accounts can operate the store. Only `ADMIN` accounts can open the promotion area. New sign-ups are created as `STAFF`.

The password encoder stores and compares raw passwords to match the requested coursework database format. Use hashed passwords before deploying any real application.

The project uses Java 17 and Spring Boot 3.4.13. Selenium UI tests can use Selenium Manager for browser-driver setup. JUnit 5, Mockito, and Selenium dependencies are included.
