package com.example.demo.service;

import com.example.demo.dto.request.CreateProductRequest;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.ProductResponse;
import com.example.demo.entity.Product;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(String search, Pageable pageable) {
        var page = StringUtils.hasText(search)
                ? productRepository.searchByName(search, pageable)
                : productRepository.findByActiveTrue(pageable);
        return PageResponse.from(page.map(ProductResponse::from));
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return ProductResponse.from(findActive(id));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest req) {
        Product product = Product.builder()
                .name(req.getName())
                .description(req.getDescription())
                .price(req.getPrice())
                .stock(req.getStock())
                .build();
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, CreateProductRequest req) {
        Product product = findActive(id);
        product.setName(req.getName());
        product.setDescription(req.getDescription());
        product.setPrice(req.getPrice());
        product.setStock(req.getStock());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public void deactivate(Long id) {
        Product product = findActive(id);
        product.setActive(false);
        productRepository.save(product);
    }

    // Used by OrderService to reserve stock — keeps the locking within this service
    @Transactional
    public Product reserveStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        if (!product.isActive()) {
            throw new BusinessException("Product is no longer available: " + product.getName());
        }
        if (product.getStock() < quantity) {
            throw new BusinessException(
                    "Insufficient stock for '" + product.getName() + "': requested " + quantity
                    + ", available " + product.getStock());
        }
        product.setStock(product.getStock() - quantity);
        return product;
    }

    private Product findActive(Long id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        if (!p.isActive()) {
            throw new ResourceNotFoundException("Product", id);
        }
        return p;
    }
}
