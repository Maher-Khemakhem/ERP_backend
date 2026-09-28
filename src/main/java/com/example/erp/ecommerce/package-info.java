/**
 * Customer-facing e-commerce module.
 *
 * Owns storefront DTOs/controllers, carts, online orders, and staff order
 * fulfilment. Product, stock, pricing, and accounting remain owned by their
 * existing modules.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "E-commerce",
        allowedDependencies = {"auth", "inventory", "pricing", "shared"}
)
package com.example.erp.ecommerce;
