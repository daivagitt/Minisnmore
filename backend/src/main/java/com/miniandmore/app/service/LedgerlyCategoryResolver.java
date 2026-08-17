package com.miniandmore.app.service;

import com.miniandmore.app.domain.Category;
import com.miniandmore.app.repository.CategoryRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class LedgerlyCategoryResolver {

    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    private final CategoryRepository categoryRepository;

    public LedgerlyCategoryResolver(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /** Resolve Ledgerly category name to a store category, creating one when missing. */
    public Optional<Category> resolve(String categoryName) {
        return resolve(categoryName, true);
    }

    public Optional<Category> resolve(String categoryName, boolean autoCreateCategory) {
        if (categoryName == null || categoryName.isBlank()) {
            return Optional.empty();
        }
        String trimmed = categoryName.trim();
        String slug = slugify(trimmed);
        Optional<Category> existing = categoryRepository.findOneBySlug(slug);
        if (existing.isPresent()) {
            return existing;
        }
        existing = categoryRepository.findOneByNameIgnoreCase(trimmed);
        if (existing.isPresent()) {
            return existing;
        }
        if (!autoCreateCategory) {
            return Optional.empty();
        }
        Category category = new Category();
        category.setName(trimmed);
        category.setSlug(uniqueCategorySlug(slug));
        category.setSortOrder(categoryRepository.findAll().size());
        category.setActive(true);
        return Optional.of(categoryRepository.save(category));
    }

    private String uniqueCategorySlug(String base) {
        String candidate = base;
        int suffix = 1;
        while (categoryRepository.findOneBySlug(candidate).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    static String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "item";
        }
        String normalized = NON_SLUG.matcher(value.toLowerCase(Locale.ROOT).trim()).replaceAll("-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        return normalized.isBlank() ? "item" : normalized;
    }
}
