package hy.CommunityBuy.service;

import hy.CommunityBuy.dto.Dtos.*;
import hy.CommunityBuy.entity.Item;
import hy.CommunityBuy.exception.BusinessException;
import hy.CommunityBuy.repository.*;
import hy.CommunityBuy.security.Actor;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemService {
  private final ItemRepository items;
  private final OrderRepository orders;
  private final Clock clock;

  public PageView<ItemView> list(String keyword, Integer status, String owner, int page, int size) {
    return list(keyword, status, owner, null, page, size);
  }

  @Transactional(readOnly = true)
  public PageView<ItemView> list(
      String keyword, Integer status, String owner, Long category, int page, int size) {
    return PageView.of(
        items
            .findAll(
                (root, q, cb) -> {
                  List<Predicate> p = new ArrayList<>();
                  if (keyword != null && !keyword.isBlank()) {
                    String pattern =
                        "%"
                            + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_")
                            + "%";
                    p.add(
                        cb.or(
                            cb.like(root.get("name"), pattern, '!'),
                            cb.like(root.get("description"), pattern, '!')));
                  }
                  if (category != null) p.add(cb.equal(root.get("categoryId"), category));
                  if (status != null) p.add(cb.equal(root.get("status"), status));
                  if (owner != null) p.add(cb.equal(root.get("ownerId"), owner));
                  return cb.and(p.toArray(Predicate[]::new));
                },
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
            .map(ItemView::of));
  }

  @Transactional(readOnly = true)
  public ItemView get(long id) {
    return ItemView.of(items.findById(id).orElseThrow(BusinessException::missing));
  }

  @Transactional
  @hy.CommunityBuy.infrastructure.BusinessAudit("ITEM_CREATE")
  public ItemView create(Actor actor, NewItem r) {
    var i = new Item();
    i.setName(r.name());
    i.setDescription(r.description());
    i.setPrice(r.price());
    i.setStock(r.stock());
    i.setStatus(r.status() == null ? 0 : r.status());
    i.setOwnerId(actor.id());
    i.setStartTime(r.startTime() == null ? clock.instant().getEpochSecond() : r.startTime());
    i.setEndTime(r.endTime() == null || r.endTime() == -1 ? null : r.endTime());
    validateTime(i);
    return ItemView.of(items.saveAndFlush(i));
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  @hy.CommunityBuy.infrastructure.BusinessAudit(value = "ITEM_UPDATE", resourceArgument = 1)
  public ItemView update(Actor actor, long id, EditItem r) {
    if (r.name() == null
        && r.description() == null
        && r.price() == null
        && r.stock() == null
        && r.status() == null
        && r.startTime() == null
        && r.endTime() == null)
      throw BusinessException.bad("No item changes; use status=-1 to close");
    var i = items.lock(id).orElseThrow(BusinessException::missing);
    actor.requireSelf(i.getOwnerId());
    if ((r.stock() != null && r.version() == null)
        || (r.version() != null && !r.version().equals(i.getVersion())))
      throw BusinessException.conflict("Supply the current item version when changing stock");
    if (r.name() != null) {
      if (r.name().isBlank()) throw BusinessException.bad("Blank item name");
      i.setName(r.name());
    }
    if (r.description() != null) i.setDescription(r.description());
    if (r.price() != null) i.setPrice(r.price());
    if (r.stock() != null) {
      if ((long) r.stock() + orders.reserved(id) > Integer.MAX_VALUE)
        throw BusinessException.conflict(
            "Available stock plus reserved stock exceeds integer capacity");
      i.setStock(r.stock());
    }
    if (r.status() != null) i.setStatus(r.status());
    if (r.startTime() != null) i.setStartTime(r.startTime());
    if (r.endTime() != null) i.setEndTime(r.endTime() == -1 ? null : r.endTime());
    validateTime(i);
    items.flush();
    return ItemView.of(i);
  }

  private void validateTime(Item i) {
    if (i.getEndTime() != null && i.getEndTime() <= i.getStartTime())
      throw BusinessException.bad("endTime must exceed startTime");
  }
}
