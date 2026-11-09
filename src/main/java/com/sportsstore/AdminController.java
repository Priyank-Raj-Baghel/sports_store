package com.sportsstore;
import jakarta.servlet.http.HttpSession; import java.util.*; import java.util.stream.Collectors;
import org.springframework.data.domain.Sort; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Controller;
import org.springframework.ui.Model; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException;

@Controller @RequestMapping("/admin")
public class AdminController {
  private final ProductRepo products; private final VariantRepo variants; private final OrderRepo orders; private final UserRepo users;
  public AdminController(ProductRepo p,VariantRepo v,OrderRepo o,UserRepo u){ products=p; variants=v; orders=o; users=u; }
  private void guard(HttpSession s){   // role check -> 403 for non-admins
    Object id=s.getAttribute("uid");
    if(id==null||!users.findById((Long)id).map(u->"ADMIN".equals(u.role)).orElse(false)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Admins only"); }

  @GetMapping String dash(HttpSession s,Model m){ guard(s);
    List<PurchaseOrder> all=orders.findAll(Sort.by(Sort.Direction.DESC,"id"));
    double rev=all.stream().filter(o->!"PENDING".equals(o.status)&&!"CANCELLED".equals(o.status)).mapToDouble(o->o.total).sum();
    m.addAttribute("products",products.findAll()).addAttribute("orders",all).addAttribute("revenue",rev)
     .addAttribute("low",variants.findAll().stream().filter(v->v.stock<=3).collect(Collectors.toList()))
     .addAttribute("statuses",List.of("PACKED","SHIPPED","DELIVERED","CANCELLED")); return "admin"; }

  @PostMapping("/product") String add(@RequestParam String name,@RequestParam String category,@RequestParam String sportType,@RequestParam double price,@RequestParam int discount,
      @RequestParam String emoji,@RequestParam String description,@RequestParam String sizes,@RequestParam String colours,@RequestParam int stock,HttpSession s){ guard(s);
    Product p=new Product(); p.name=name; p.category=category; p.sportType=sportType; p.price=price; p.discount=discount; p.emoji=emoji; p.description=description; products.save(p);
    int n=0; for(String c:colours.split(",")) for(String sz:sizes.split(",")){ Variant v=new Variant(); v.product=p; v.colour=c.trim(); v.size=sz.trim(); v.sku="SKU"+p.id+"-"+(n++); v.stock=stock; variants.save(v); }
    return "redirect:/admin"; }
  @PostMapping("/stock") String stock(@RequestParam Long variantId,@RequestParam int stock,HttpSession s){ guard(s);
    Variant v=variants.findById(variantId).orElseThrow(); v.stock=Math.max(stock,0); variants.save(v); return "redirect:/admin"; }
  @PostMapping("/order-status") String status(@RequestParam Long id,@RequestParam String status,HttpSession s){ guard(s);
    PurchaseOrder o=orders.findById(id).orElseThrow(); o.status=status; orders.save(o); return "redirect:/admin"; }
}
