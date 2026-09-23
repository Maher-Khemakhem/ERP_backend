/**
 * Customer-facing e-commerce module.
 *
 * Owns storefront DTOs/controllers and, in the next phase, carts and online
 * orders. Product, stock, pricing, and accounting remain owned by their
 * existing modules.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "E-commerce",
        allowedDependencies = {"auth", "inventory", "pricing", "shared"}
)
package com.example.erp.ecommerce;
