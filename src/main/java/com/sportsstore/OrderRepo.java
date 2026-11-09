package com.sportsstore;
import java.util.List; import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderRepo extends JpaRepository<PurchaseOrder,Long> {
  List<PurchaseOrder> findByUserOrderByIdDesc(User u); PurchaseOrder findByPaymentRef(String ref);
}
