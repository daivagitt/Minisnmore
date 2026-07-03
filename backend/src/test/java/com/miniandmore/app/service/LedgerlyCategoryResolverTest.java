package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.Category;
import com.miniandmore.app.repository.CategoryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerlyCategoryResolverTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private LedgerlyCategoryResolver resolver;

    @Test
    void slugify_normalizesName() {
        assertThat(LedgerlyCategoryResolver.slugify("Baby Wear & More")).isEqualTo("baby-wear-more");
        assertThat(LedgerlyCategoryResolver.slugify("  ")).isEqualTo("item");
    }

    @Test
    void resolve_blankName_returnsEmpty() {
        assertThat(resolver.resolve("  ")).isEmpty();
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void resolve_existingSlug_returnsCategory() {
        Category category = new Category().name("Essentials").slug("essentials");
        when(categoryRepository.findOneBySlug("essentials")).thenReturn(Optional.of(category));

        Optional<Category> result = resolver.resolve("Essentials");

        assertThat(result).contains(category);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void resolve_existingNameWhenSlugMissing_returnsCategory() {
        Category category = new Category().name("Essentials").slug("essentials");
        when(categoryRepository.findOneBySlug("essentials")).thenReturn(Optional.empty());
        when(categoryRepository.findOneByNameIgnoreCase("Essentials")).thenReturn(Optional.of(category));

        assertThat(resolver.resolve("Essentials")).contains(category);
    }

    @Test
    void resolve_missingCategory_autoCreatesWhenEnabled() {
        when(categoryRepository.findOneBySlug("newborn")).thenReturn(Optional.empty());
        when(categoryRepository.findOneByNameIgnoreCase("Newborn")).thenReturn(Optional.empty());
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Category> result = resolver.resolve("Newborn", true);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getName()).isEqualTo("Newborn");
        assertThat(result.orElseThrow().getSlug()).isEqualTo("newborn");
        assertThat(result.orElseThrow().getActive()).isTrue();
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void resolve_missingCategory_skipsCreateWhenDisabled() {
        when(categoryRepository.findOneBySlug("newborn")).thenReturn(Optional.empty());
        when(categoryRepository.findOneByNameIgnoreCase("Newborn")).thenReturn(Optional.empty());

        assertThat(resolver.resolve("Newborn", false)).isEmpty();
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void resolve_duplicateSlug_appendsSuffix() {
        when(categoryRepository.findOneBySlug("baby")).thenReturn(Optional.empty(), Optional.of(new Category()));
        when(categoryRepository.findOneBySlug("baby-1")).thenReturn(Optional.empty());
        when(categoryRepository.findOneByNameIgnoreCase("Baby")).thenReturn(Optional.empty());
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Category> result = resolver.resolve("Baby", true);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getSlug()).isEqualTo("baby-1");
    }
}
