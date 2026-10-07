package com.poly.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

@EnabledIfSystemProperty(named = "taskio.ui", matches = "true")
class LoginAutomationTest extends UiTestSupport {

    @Test
    void employeeCanRegisterAndLogIn() {
        String username = unique("uiuser");
        String password = "Taskio123";

        driver.get(baseUrl + "/register");
        driver.findElement(By.name("username")).sendKeys(username);
        driver.findElement(By.name("password")).sendKeys(password);
        driver.findElement(By.name("confirmPassword")).sendKeys(password);
        driver.findElement(By.cssSelector("form button[type='submit']")).click();

        wait.until(ExpectedConditions.urlContains("/login"));
        assertTrue(driver.getPageSource().contains("Tạo tài khoản thành công"));

        login(username, password);
        assertTrue(driver.getPageSource().contains("Tổng quan"));
    }
}
