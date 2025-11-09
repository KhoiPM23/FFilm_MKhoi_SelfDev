// src/main/java/com/example/project/service/CategoryService.java
package com.example.project.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.project.model.Category;
import com.example.project.repository.CategoryRepository;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Optional<Category> getCategoryByName(String name) {
        return categoryRepository.findByName(name);
    }

    public Category getOrCreateCategory(String name) {
        return categoryRepository.findByName(name)
                .orElseGet(() -> {
                    Category c = new Category();
                    c.setName(name);
                    c.setCategoryParentID(null);
                    return categoryRepository.save(c);
                });
    }
}