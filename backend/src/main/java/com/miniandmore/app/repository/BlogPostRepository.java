package com.miniandmore.app.repository;

import com.miniandmore.app.domain.BlogPost;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the BlogPost entity.
 */
@SuppressWarnings("unused")
@Repository
public interface BlogPostRepository extends JpaRepository<BlogPost, Long>, JpaSpecificationExecutor<BlogPost> {
    List<BlogPost> findAllByPublishedTrueOrderByPublishedAtDesc();

    List<BlogPost> findAllByOrderByIdDesc();

    Optional<BlogPost> findOneBySlugAndPublishedTrue(String slug);

    Optional<BlogPost> findOneBySlug(String slug);
}
