package com.finance.habits;

import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Original feature controller — Campus Habits (/habits).
 * Follows the same auth pattern as Expense/Budget controllers + user-scoped data.
 */
@Controller
@RequestMapping("/habits")
public class HabitController {

    private final HabitService habitService;
    private final UserRepository userRepository;

    public HabitController(HabitService habitService, UserRepository userRepository) {
        this.habitService = habitService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String viewHabits(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        HabitService.HabitReport report = habitService.buildReport(user);

        model.addAttribute("user", user);
        model.addAttribute("results", report.results());
        model.addAttribute("overallScore", report.overallScore());
        model.addAttribute("grade", report.grade());
        model.addAttribute("totalIncome", report.totalIncome());
        model.addAttribute("totalExpenses", report.totalExpenses());
        model.addAttribute("savingsRate", report.savingsRate());
        model.addAttribute("hasData", report.hasData());
        return "habits";
    }
}
