package com.gas.ecommerce.modules.order.annotation;

import java.lang.annotation.*;

/** Validates the checkout key; durable replay and concurrency control live in OrderSubmissionStore. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckoutIdempotent { }
