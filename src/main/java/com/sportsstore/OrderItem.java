package com.sportsstore;
import jakarta.persistence.*;
@Entity
public class OrderItem {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @ManyToOne(fetch=FetchType.EAGER) public PurchaseOrder order;
  @ManyToOne(fetch=FetchType.EAGER) public Variant variant;
  public int qty; public double unitPrice;
}
