package com.sportsstore;
import jakarta.persistence.*; import java.time.LocalDateTime; import java.time.format.DateTimeFormatter; import java.util.*;
@Entity @Table(name="orders")
public class PurchaseOrder {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @ManyToOne(fetch=FetchType.EAGER) public User user;
  public double total; public String status="PENDING", method, paymentRef; @Column(length=500) public String address;
  public LocalDateTime createdAt=LocalDateTime.now();
  @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true) public List<OrderItem> items=new ArrayList<>();
  public String date(){ return createdAt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")); }
}
