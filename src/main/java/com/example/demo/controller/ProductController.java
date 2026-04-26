package com.example.demo.controller;

import com.example.demo.dto.request.CreateProductRequest;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.ProductResponse;
import com.example.demo.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Catalog management. GET endpoints are public; writes require ADMIN role")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "List active products (paginated, optional name search)")
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productService.list(search, PageRequest.of(page, size, Sort.by("name")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single product by ID")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new product (ADMIN only)")
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update product details (ADMIN only)")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody CreateProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Soft-delete a product (ADMIN only)")
    public void delete(@PathVariable Long id) {
        productService.deactivate(id);
    }
}
