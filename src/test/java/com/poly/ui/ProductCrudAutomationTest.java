package com.poly.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

@EnabledIfSystemProperty(named = "taskio.ui", matches = "true")
class ProductCrudAutomationTest extends UiTestSupport {

    @Test
    void employeeCanCreateUpdateAndDeleteProduct() {
        registerAndLogin();

        String sku = unique("UIT");
        String productName = unique("Selenium product ");

        driver.get(baseUrl + "/products/new");
        driver.findElement(By.name("sku")).sendKeys(sku);
        driver.findElement(By.name("name")).sendKeys(productName);
        driver.findElement(By.name("price")).sendKeys("125000");
        driver.findElement(By.name("stock")).sendKeys("8");
        driver.findElement(By.cssSelector("form button[type='submit']")).click();

        wait.until(ExpectedConditions.urlToBe(baseUrl + "/products"));
        WebElement row = productRow(sku);
        assertTrue(row.getText().contains(productName));
        row.findElement(By.cssSelector("a[href*='/edit']")).click();

        String updatedName = productName + " updated";
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("name")));
        WebElement nameInput = driver.findElement(By.name("name"));
        nameInput.clear();
        nameInput.sendKeys(updatedName);
        driver.findElement(By.cssSelector("form button[type='submit']")).click();

        wait.until(ExpectedConditions.urlToBe(baseUrl + "/products"));
        row = productRow(sku);
        assertTrue(row.getText().contains(updatedName));
        row.findElement(By.cssSelector("form button[type='submit']")).click();
        driver.switchTo().alert().accept();

        wait.until(ExpectedConditions.invisibilityOfElementLocated(
                By.xpath("//tr[td[normalize-space()='" + sku + "']]")));
        assertTrue(driver.getPageSource().contains("Đã xóa sản phẩm."));
    }

    private void registerAndLogin() {
        String username = unique("uiprod");
        String password = "Taskio123";
        driver.get(baseUrl + "/register");
        driver.findElement(By.name("username")).sendKeys(username);
        driver.findElement(By.name("password")).sendKeys(password);
        driver.findElement(By.name("confirmPassword")).sendKeys(password);
        driver.findElement(By.cssSelector("form button[type='submit']")).click();
        wait.until(ExpectedConditions.urlContains("/login"));
        login(username, password);
    }

    private WebElement productRow(String sku) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//tr[td[normalize-space()='" + sku + "']]")));
    }
}
