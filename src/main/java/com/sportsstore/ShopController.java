package com.sportsstore;
import jakarta.servlet.http.HttpSession; import java.util.*; import java.util.stream.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import org.springframework.stereotype.Controller;
import org.springframework.ui.Model; import org.springframework.web.bind.annotation.*; import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.beans.factory.annotation.Autowired; import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
class Globals {   // adds logged-in user + cart count to every page
  @Autowired UserRepo users;
  @ModelAttribute("me") User me(HttpSession s){ Object id=s.getAttribute("uid"); return id==null?null:users.findById((Long)id).orElse(null); }
  @ModelAttribute("cartCount") int count(HttpSession s){ Object c=s.getAttribute("cart"); return c==null?0:((Map<?,?>)c).values().stream().mapToInt(v->(Integer)v).sum(); }
}

@Controller
public class ShopController {
  private final ProductRepo products; private final VariantRepo variants; private final UserRepo users; private final OrderRepo orders;
  private final StoreService svc; private final BCryptPasswordEncoder enc;
  public ShopController(ProductRepo p,VariantRepo v,UserRepo u,OrderRepo o,StoreService s,BCryptPasswordEncoder e){ products=p; variants=v; users=u; orders=o; svc=s; enc=e; }

  @SuppressWarnings("unchecked")
  static Map<Long,Integer> cart(HttpSession s){ Object c=s.getAttribute("cart"); if(c==null){ c=new LinkedHashMap<Long,Integer>(); s.setAttribute("cart",c); } return (Map<Long,Integer>)c; }

  @GetMapping({"/","/products"})
  String home(@RequestParam(required=false) String q,@RequestParam(required=false) String category,@RequestParam(required=false) String sport,
              @RequestParam(required=false) String size,@RequestParam(required=false) Double max,@RequestParam(defaultValue="new") String sort,Model m){
    Stream<Product> s=products.findAll().stream();
    if(q!=null&&!q.isBlank()) s=s.filter(p->(p.name+" "+p.description).toLowerCase().contains(q.toLowerCase()));
    if(category!=null&&!category.isBlank()) s=s.filter(p->p.category.equals(category));
    if(sport!=null&&!sport.isBlank()) s=s.filter(p->p.sportType.equals(sport));
    if(size!=null&&!size.isBlank()) s=s.filter(p->p.variants.stream().anyMatch(v->v.size.equals(size)&&v.stock>0));
    if(max!=null) s=s.filter(p->p.finalPrice()<=max);
    List<Product> list=s.collect(Collectors.toList());
    if(!sort.equals("new")){ list.sort(Comparator.comparingDouble(Product::finalPrice)); if(sort.equals("high")) Collections.reverse(list); }
    else list.sort((a,b)->Long.compare(b.id,a.id));
    m.addAttribute("products",list).addAttribute("q",q).addAttribute("category",category).addAttribute("sport",sport).addAttribute("size",size).addAttribute("max",max).addAttribute("sort",sort)
     .addAttribute("categories",List.of("Men","Women","Kids")).addAttribute("sports",List.of("Running","Gym","Football","Cricket","Yoga")).addAttribute("sizes",List.of("S","M","L","XL"));
    return "index"; }

  @GetMapping("/product/{id}")
  String product(@PathVariable Long id,Model m){ Product p=products.findById(id).orElseThrow();
    m.addAttribute("p",p).addAttribute("similar",svc.similar(p,4)).addAttribute("together",svc.together(p,4)); return "product"; }

  // ----- cart -----
  @GetMapping("/cart") String cartPage(HttpSession s,Model m){
    List<StoreService.Line> ls=svc.lines(cart(s)); double sub=ls.stream().mapToDouble(l->l.total).sum();
    m.addAttribute("lines",ls).addAttribute("sub",sub).addAttribute("delivery",svc.delivery(sub)); return "cart"; }
  @PostMapping("/cart/add") String add(@RequestParam Long variantId,@RequestParam(defaultValue="1") int qty,HttpSession s,RedirectAttributes ra){
    Variant v=variants.findById(variantId).orElseThrow(); Map<Long,Integer> c=cart(s); int n=c.getOrDefault(variantId,0)+Math.max(qty,1);
    if(n>v.stock){ ra.addFlashAttribute("msg","Only "+v.stock+" left in that size/colour"); return "redirect:/product/"+v.product.id; }
    c.put(variantId,n); ra.addFlashAttribute("msg","Added to cart"); return "redirect:/cart"; }
  @PostMapping("/cart/update") String upd(@RequestParam Long variantId,@RequestParam int qty,HttpSession s){
    Variant v=variants.findById(variantId).orElseThrow(); if(qty<=0) cart(s).remove(variantId); else cart(s).put(variantId,Math.min(qty,v.stock)); return "redirect:/cart"; }
  @PostMapping("/cart/remove") String rm(@RequestParam Long variantId,HttpSession s){ cart(s).remove(variantId); return "redirect:/cart"; }

  // ----- checkout & payment -----
  @GetMapping("/checkout") String checkout(HttpSession s,Model m){
    if(s.getAttribute("uid")==null) return "redirect:/login"; if(cart(s).isEmpty()) return "redirect:/cart"; cartPage(s,m); return "checkout"; }
  @PostMapping("/checkout") String place(@RequestParam String address,@RequestParam String method,HttpSession s,RedirectAttributes ra){
    Long uid=(Long)s.getAttribute("uid"); if(uid==null) return "redirect:/login";
    try{ PurchaseOrder o=svc.placeOrder(users.findById(uid).orElseThrow(),cart(s),address,method); cart(s).clear();
      if("ONLINE".equals(method)) return "redirect:/pay/"+o.id; ra.addFlashAttribute("msg","Order placed (cash on delivery)!"); return "redirect:/orders"; }
    catch(IllegalStateException e){ ra.addFlashAttribute("msg",e.getMessage()); return "redirect:/cart"; } }
  @GetMapping("/pay/{id}") String pay(@PathVariable Long id,HttpSession s,Model m,RedirectAttributes ra){
    PurchaseOrder o=orders.findById(id).orElseThrow();
    if(!o.user.id.equals(s.getAttribute("uid"))||!"PENDING".equals(o.status)) return "redirect:/orders";
    try{ o=svc.startPayment(o); }catch(IllegalStateException e){ ra.addFlashAttribute("msg",e.getMessage()); return "redirect:/orders"; }
    String pid="pay_"+UUID.randomUUID().toString().substring(0,8);
    m.addAttribute("o",o).addAttribute("live",svc.live()).addAttribute("keyId",svc.keyId).addAttribute("paise",Math.round(o.total*100))
     .addAttribute("pid",pid).addAttribute("sig",svc.sign(o.paymentRef,pid)); return "pay"; }
  @PostMapping("/payment/verify") String verify(@RequestParam("order_ref") String ref,@RequestParam("payment_id") String pid,@RequestParam String signature,RedirectAttributes ra){
    ra.addFlashAttribute("msg",svc.verify(ref,pid,signature)?"Payment verified - order confirmed!":"Payment verification FAILED - order not confirmed"); return "redirect:/orders"; }
  @GetMapping("/orders") String myOrders(HttpSession s,Model m){
    Long uid=(Long)s.getAttribute("uid"); if(uid==null) return "redirect:/login";
    m.addAttribute("orders",orders.findByUserOrderByIdDesc(users.findById(uid).orElseThrow())); return "orders"; }

  // ----- auth -----
  @GetMapping("/login") String loginPage(Model m){ m.addAttribute("mode","login"); return "auth"; }
  @GetMapping("/register") String regPage(Model m){ m.addAttribute("mode","register"); return "auth"; }
  @PostMapping("/login") String login(@RequestParam String email,@RequestParam String password,HttpSession s,RedirectAttributes ra){
    User u=users.findByEmail(email.trim().toLowerCase());
    if(u==null||!enc.matches(password,u.passwordHash)){ ra.addFlashAttribute("msg","Invalid email or password"); return "redirect:/login"; }
    s.setAttribute("uid",u.id); return "redirect:/"; }
  @PostMapping("/register") String register(@RequestParam String name,@RequestParam String email,@RequestParam String password,HttpSession s,RedirectAttributes ra){
    String e=email.trim().toLowerCase();
    if(password.length()<6){ ra.addFlashAttribute("msg","Password must be at least 6 characters"); return "redirect:/register"; }
    if(users.findByEmail(e)!=null){ ra.addFlashAttribute("msg","Email already registered"); return "redirect:/register"; }
    User u=new User(); u.name=name; u.email=e; u.passwordHash=enc.encode(password); users.save(u); s.setAttribute("uid",u.id); return "redirect:/"; }
  @GetMapping("/logout") String logout(HttpSession s){ s.invalidate(); return "redirect:/"; }
}
