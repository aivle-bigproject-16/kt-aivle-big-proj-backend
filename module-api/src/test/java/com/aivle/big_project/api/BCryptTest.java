package com.aivle.big_project.api;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class BCryptTest {
    @Test
    public void test() {
        System.out.println("==== BCRYPT HASH ====");
        System.out.println(new BCryptPasswordEncoder().encode("1"));
        System.out.println("=====================");
    }
}
