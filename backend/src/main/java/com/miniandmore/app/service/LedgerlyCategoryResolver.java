package com.miniandmore.app.service;

import com.miniandmore.app.domain.Category;
import com.miniandmore.app.domain.LedgerlySetting;
import com.miniandmore.app.repository.CategoryRepository;
import com.miniandmore.app.repository.LedgerlySettingRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class LedgerlyCategoryResolver {

    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    private final CategoryRepository categoryRepository;
    private final LedgerlySettingRepository ledgerlySettingRepository;

    public LedgerlyCategoryResolver(CategoryRepository categoryRepository, LedgerlySettingRepository ledgerlySettingRepository) {
        this.categoryRepository = categoryRepository;
        this.ledgerlySettingRepository = ledgerlySettingRepository;
    }

    public Optional<Category> resolve(String categoryName) {
        boolean autoCreate = ledgerlySettingRepository
            .findFirstByOrderByIdAsc()
            .map(LedgerlySetting::getPullProductsAutoCreateCategory)
            .orElse(false);
        return resolve(categoryName, autoCreate);
    }

    public Optional<Category> resolve(String categoryName, boolean autoCreateCategory) {
        if (categoryName == null || categoryName.isBlank()) {
            return Optional.empty();
        }
        String slug = slugify(categoryName);
        Optional<Category> existing = categoryRepository.findOneBySlug(slug);
        if (existing.isPresent()) {
            return existing;
        }
        if (!autoCreateCategory) {
            return Optional.empty();
        }
        Category category = new Category();
        category.setName(categoryName.trim());
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
        normalized = normalized.replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "item" : normalized;
    }
}
