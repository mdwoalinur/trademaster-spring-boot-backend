package com.trademaster.ims.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)   // only methods
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {
    String action();          // "CREATE", "UPDATE", "DELETE", "LOGIN", "LOGOUT"
    String entityType();      // "Product", "Sale", "User", etc.
}