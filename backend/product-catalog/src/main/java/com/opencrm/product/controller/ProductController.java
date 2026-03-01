package com.opencrm.product.controller;

import com.opencrm.product.domain.Product;
import com.opencrm.product.domain.ProductStatus;
import com.opencrm.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping
    public Product createProduct(@RequestBody Product product) {
        return productService.createProduct(product);
    }

    @PutMapping("/{id}")
    public Product updateProduct(@PathVariable Long id, @RequestBody Product product) {
        return productService.updateProduct(id, product);
    }

    @PatchMapping("/{id}/status")
    public Product updateStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        return productService.updateStatus(id, request.status());
    }
    
    public record StatusRequest(ProductStatus status) {}
}
