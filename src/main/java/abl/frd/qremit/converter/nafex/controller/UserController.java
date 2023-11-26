package abl.frd.qremit.converter.nafex.controller;

import abl.frd.qremit.converter.nafex.model.User;
import abl.frd.qremit.converter.nafex.service.MyUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collection;
import java.util.List;

@Controller
public class UserController {
    private final MyUserDetailsService myUserDetailsService;
    @Autowired
    public UserController(MyUserDetailsService myUserDetailsService) {
        this.myUserDetailsService = myUserDetailsService;
    }

    @RequestMapping("/allUsers")
    public String loadAllUser(Model model){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        List<User> userList;
        for (final GrantedAuthority grantedAuthority : authorities) {
            String authorityName = grantedAuthority.getAuthority();
            if (authorityName.equals("ROLE_SUPERADMIN")) {
                userList = myUserDetailsService.loadAllUser();
                model.addAttribute("UserList", userList);
                return "/pages/superAdminUserListPage";
            }
            if (authorityName.equals("ROLE_ADMIN")) {
                userList = myUserDetailsService.loadUsersOnly();
                model.addAttribute("UserList", userList);
                return "/pages/adminUserListPage";
            }
        }
        return "/allUsers";
    }
}
