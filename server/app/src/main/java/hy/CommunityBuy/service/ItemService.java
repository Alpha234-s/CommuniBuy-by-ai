package hy.CommunityBuy.service;

import hy.CommunityBuy.entity.Item;
import hy.CommunityBuy.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ItemService {

    @Autowired
    private ItemRepository itemRepository;

    public List<Item> getAllItems(Integer status, String ownerId) {
        if (status != null) {
            return itemRepository.findByStatusGreaterThanEqual(status);
        }
        if (ownerId != null) {
            return itemRepository.findByOwnerId(ownerId);
        }
        return itemRepository.findAll();
    }

    public Optional<Item> getItem(Long id) {
        return itemRepository.findById(id);
    }

    @Transactional
    public Item createItem(Item item) {
        return itemRepository.save(item);
    }

    @Transactional
    public Item updateItem(Long id, Item updateInfo) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        
        if (updateInfo.getName() != null) item.setName(updateInfo.getName());
        if (updateInfo.getDescription() != null) item.setDescription(updateInfo.getDescription());
        if (updateInfo.getPrice() != null) item.setPrice(updateInfo.getPrice());
        if (updateInfo.getStock() != null) item.setStock(updateInfo.getStock());
        if (updateInfo.getStatus() != null) item.setStatus(updateInfo.getStatus());
        if (updateInfo.getStartTime() != null) item.setStartTime(updateInfo.getStartTime());
        if (updateInfo.getEndTime() != null) item.setEndTime(updateInfo.getEndTime());
        
        return itemRepository.save(item);
    }

    @Transactional
    public void deleteItem(Long id) {
        itemRepository.deleteById(id);
    }
}