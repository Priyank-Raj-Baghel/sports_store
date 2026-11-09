package com.sportsstore;
import jakarta.persistence.*;
@Entity
public class Variant {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @ManyToOne(fetch=FetchType.EAGER) public Product product;
  public String size, colour; @Column(unique=true) public String sku; public int stock;
}
