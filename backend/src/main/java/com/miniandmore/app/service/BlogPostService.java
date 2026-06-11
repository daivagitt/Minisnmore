package com.miniandmore.app.service;

import com.miniandmore.app.domain.BlogPost;
import com.miniandmore.app.repository.BlogPostRepository;
import com.miniandmore.app.service.dto.BlogPostDTO;
import com.miniandmore.app.service.mapper.BlogPostMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.miniandmore.app.domain.BlogPost}.
 */
@Service
@Transactional
public class BlogPostService {

    private static final Logger LOG = LoggerFactory.getLogger(BlogPostService.class);

    private final BlogPostRepository blogPostRepository;

    private final BlogPostMapper blogPostMapper;

    public BlogPostService(BlogPostRepository blogPostRepository, BlogPostMapper blogPostMapper) {
        this.blogPostRepository = blogPostRepository;
        this.blogPostMapper = blogPostMapper;
    }

    /**
     * Save a blogPost.
     *
     * @param blogPostDTO the entity to save.
     * @return the persisted entity.
     */
    public BlogPostDTO save(BlogPostDTO blogPostDTO) {
        LOG.debug("Request to save BlogPost : {}", blogPostDTO);
        BlogPost blogPost = blogPostMapper.toEntity(blogPostDTO);
        blogPost = blogPostRepository.save(blogPost);
        return blogPostMapper.toDto(blogPost);
    }

    /**
     * Update a blogPost.
     *
     * @param blogPostDTO the entity to save.
     * @return the persisted entity.
     */
    public BlogPostDTO update(BlogPostDTO blogPostDTO) {
        LOG.debug("Request to update BlogPost : {}", blogPostDTO);
        BlogPost blogPost = blogPostMapper.toEntity(blogPostDTO);
        blogPost = blogPostRepository.save(blogPost);
        return blogPostMapper.toDto(blogPost);
    }

    /**
     * Partially update a blogPost.
     *
     * @param blogPostDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<BlogPostDTO> partialUpdate(BlogPostDTO blogPostDTO) {
        LOG.debug("Request to partially update BlogPost : {}", blogPostDTO);

        return blogPostRepository
            .findById(blogPostDTO.getId())
            .map(existingBlogPost -> {
                blogPostMapper.partialUpdate(existingBlogPost, blogPostDTO);

                return existingBlogPost;
            })
            .map(blogPostRepository::save)
            .map(blogPostMapper::toDto);
    }

    /**
     * Get one blogPost by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<BlogPostDTO> findOne(Long id) {
        LOG.debug("Request to get BlogPost : {}", id);
        return blogPostRepository.findById(id).map(blogPostMapper::toDto);
    }

    /**
     * Delete the blogPost by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete BlogPost : {}", id);
        blogPostRepository.deleteById(id);
    }
}
