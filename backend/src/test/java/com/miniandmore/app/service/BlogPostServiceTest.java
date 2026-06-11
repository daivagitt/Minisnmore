package com.miniandmore.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.miniandmore.app.domain.BlogPost;
import com.miniandmore.app.repository.BlogPostRepository;
import com.miniandmore.app.service.dto.BlogPostDTO;
import com.miniandmore.app.service.mapper.BlogPostMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlogPostServiceTest {

    private static final Long ID = 1L;

    @Mock
    private BlogPostRepository blogPostRepository;

    @Mock
    private BlogPostMapper blogPostMapper;

    @InjectMocks
    private BlogPostService blogPostService;

    @Test
    void save() {
        BlogPostDTO input = new BlogPostDTO();
        BlogPost entity = new BlogPost().id(ID);
        BlogPost saved = new BlogPost().id(ID);
        BlogPostDTO expected = new BlogPostDTO();
        expected.setId(ID);

        when(blogPostMapper.toEntity(input)).thenReturn(entity);
        when(blogPostRepository.save(entity)).thenReturn(saved);
        when(blogPostMapper.toDto(saved)).thenReturn(expected);

        BlogPostDTO result = blogPostService.save(input);

        assertThat(result).isSameAs(expected);
        verify(blogPostMapper).toEntity(input);
        verify(blogPostRepository).save(entity);
        verify(blogPostMapper).toDto(saved);
    }

    @Test
    void update() {
        BlogPostDTO input = new BlogPostDTO();
        input.setId(ID);
        BlogPost entity = new BlogPost().id(ID);
        BlogPost saved = new BlogPost().id(ID);
        BlogPostDTO expected = new BlogPostDTO();
        expected.setId(ID);

        when(blogPostMapper.toEntity(input)).thenReturn(entity);
        when(blogPostRepository.save(entity)).thenReturn(saved);
        when(blogPostMapper.toDto(saved)).thenReturn(expected);

        BlogPostDTO result = blogPostService.update(input);

        assertThat(result).isSameAs(expected);
        verify(blogPostMapper).toEntity(input);
        verify(blogPostRepository).save(entity);
    }

    @Test
    void partialUpdate_found() {
        BlogPostDTO input = new BlogPostDTO();
        input.setId(ID);
        BlogPost existing = new BlogPost().id(ID);
        BlogPostDTO expected = new BlogPostDTO();
        expected.setId(ID);

        when(blogPostRepository.findById(ID)).thenReturn(Optional.of(existing));
        when(blogPostRepository.save(existing)).thenReturn(existing);
        when(blogPostMapper.toDto(existing)).thenReturn(expected);

        Optional<BlogPostDTO> result = blogPostService.partialUpdate(input);

        assertThat(result).contains(expected);
        verify(blogPostMapper).partialUpdate(existing, input);
        verify(blogPostRepository).save(existing);
    }

    @Test
    void partialUpdate_notFound() {
        BlogPostDTO input = new BlogPostDTO();
        input.setId(ID);
        when(blogPostRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<BlogPostDTO> result = blogPostService.partialUpdate(input);

        assertThat(result).isEmpty();
    }

    @Test
    void findOne_found() {
        BlogPost entity = new BlogPost().id(ID);
        BlogPostDTO dto = new BlogPostDTO();
        dto.setId(ID);
        when(blogPostRepository.findById(ID)).thenReturn(Optional.of(entity));
        when(blogPostMapper.toDto(entity)).thenReturn(dto);

        Optional<BlogPostDTO> result = blogPostService.findOne(ID);

        assertThat(result).contains(dto);
    }

    @Test
    void findOne_notFound() {
        when(blogPostRepository.findById(ID)).thenReturn(Optional.empty());

        Optional<BlogPostDTO> result = blogPostService.findOne(ID);

        assertThat(result).isEmpty();
    }

    @Test
    void delete() {
        blogPostService.delete(ID);
        verify(blogPostRepository).deleteById(ID);
    }

}
