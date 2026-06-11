package com.miniandmore.app.repository;

import com.miniandmore.app.domain.OrderReturn;
import com.miniandmore.app.domain.enumeration.OrderReturnStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderReturnRepository extends JpaRepository<OrderReturn, Long> {
    Optional<OrderReturn> findOneByReturnNumber(String returnNumber);

    @EntityGraph(attributePaths = { "order" })
    @Query("select r from OrderReturn r where r.userLogin = :login order by r.requestedAt desc")
    List<OrderReturn> findAllByUserLoginOrderByRequestedAtDesc(@Param("login") String login);

    @EntityGraph(attributePaths = { "order" })
    List<OrderReturn> findAllByStatusInOrderByRequestedAtDesc(Collection<OrderReturnStatus> statuses);

    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<OrderReturnStatus> statuses);
}
