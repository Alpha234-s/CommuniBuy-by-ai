package hy.CommunityBuy.service;

import hy.CommunityBuy.repository.ItemRepository;
import hy.CommunityBuy.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AdminService {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private OrderRepository orderRepository;

    public Map<String, Object> getGlobalStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("userCount", 0); // 实际应从 UserRepository 获取
        stats.put("itemCount", itemRepository.count());
        stats.put("orderCount", orderRepository.count());
        stats.put("totalRevenue", 0); // 实际应根据订单金额累加
        stats.put("ongoingItemCount", itemRepository.findByStatusGreaterThanEqual(0).size());
        return stats;
    }
}