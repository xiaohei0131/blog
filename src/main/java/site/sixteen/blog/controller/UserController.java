package site.sixteen.blog.controller;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import site.sixteen.blog.entity.User;
import site.sixteen.blog.entity.UserAuth;
import site.sixteen.blog.entity.UserLog;
import site.sixteen.blog.exception.UserLoginException;
import site.sixteen.blog.exception.UserPasswordException;
import site.sixteen.blog.exception.UserRegisterException;
import site.sixteen.blog.properties.ConstantProperties;
import site.sixteen.blog.service.UserService;

import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author panhainan@yeah.net(@link http://sixteen.site)
 **/
@Slf4j
@Controller
public class UserController {

    @Autowired
    ConstantProperties constantProperties;

    @Value("${spring.http.multipart.location}")
    private String fileUploadLocation;


    @Autowired
    private UserService userService;

    @GetMapping({"/index", "/"})
    public String index() {
        return "index";
    }

    @GetMapping("/register")
    public String register(Model model) {
        UserAuth userAuth = new UserAuth();
        model.addAttribute("userAuth", userAuth);
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid UserAuth userAuth, BindingResult bindingResult, RedirectAttributes redirectAttributes) throws UserRegisterException {
        if (bindingResult.hasErrors()) {
            return "register";
        }
        userService.register(userAuth);
        log.info("注册成功！");
        redirectAttributes.addFlashAttribute("successMsg", "注册成功！您可以登录了。");
        return "redirect:/login";
    }


    @GetMapping(value = "/login")
    public String login(Model model) {
        UserAuth userAuth = new UserAuth();
        model.addAttribute("userAuth", userAuth);
        return "login";
    }

    @PostMapping("/login")
    public String login(@Valid UserAuth userAuth, BindingResult bindingResult) throws UserLoginException {
        if (bindingResult.hasErrors()) {
            return "login";
        }
        Subject currentSubject = SecurityUtils.getSubject();
        if (!currentSubject.isAuthenticated()) {
            UsernamePasswordToken token = new UsernamePasswordToken(userAuth.getUsername(), userAuth.getPassword());
            try {
                // 执行登录.
                currentSubject.login(token);
            } catch (UnknownAccountException uae) {
                // 若没有指定的账户, 则 shiro 将会抛出 UnknownAccountException 异常.
                log.info("{}", uae.getMessage());
                throw new UserLoginException("用户名或密码错误！");
            } catch (IncorrectCredentialsException lae) {
                // 若账户存在, 但密码不匹配, 则 shiro 会抛出 IncorrectCredentialsException 异常。
                log.info("{}", lae.getMessage());
                throw new UserLoginException("用户名或密码错误！");
            } catch (LockedAccountException lae) {
                // 用户被锁定的异常 LockedAccountException
                log.info("{}", lae.getMessage());
                throw new UserLoginException("用户已被锁定！");
            } catch (AuthenticationException e) {
                // 所有认证时异常的父类.
                log.info("{}", e.getMessage());
                //登录失败
                throw new UserLoginException(e.getLocalizedMessage());
            }
        }
        return "redirect:/my";
    }

    public void preformLogin() {

    }

    @GetMapping("/my")
    public String my(Model model) {
        model.addAttribute("user", userService.getCurrentUser());
        log.info("{}", model);
        return "user/info";
    }

    @GetMapping("/my/info")
    public String myInfo(Model model) {
        model.addAttribute("user", userService.getCurrentUser());
        log.info("{}", model);
        return "user/info-edit";
    }


    @PostMapping("/my/info")
    public String myInfo(@RequestPart(required = false) MultipartFile file, User user, RedirectAttributes redirectAttributes) throws IOException {
        log.info("{}", user);
        if (!file.isEmpty()) {
            log.info("file type:{}", file.getContentType());
            if (constantProperties.getUserFaceAllowType().contains(file.getContentType())) {
                String suffixName = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
                String fileName = user.getUsername() + suffixName;
                //保存图片
                file.transferTo(new File(fileName));
                String filePath = fileUploadLocation + "/" + fileName;
                File oldFile = new File(filePath);
                //按指定大小把图片进行缩和放（会遵循原图高宽比例）
                //此处把图片压成160的缩略图
                int width = 160, height = 160;
                Thumbnails.of(oldFile).size(width, height).toFile(oldFile);
                user.setFace(fileName);
            } else {
                log.info("文件类型错误！");
                redirectAttributes.addFlashAttribute("errorMsg", "头像修改失败，上传的文件文件类型不允许！");
            }
        }
        userService.updateMyInfo(user);
        return "redirect:/my";
    }

    @GetMapping("/my/setting")
    public String mySetting() {
        return "user/setting";
    }

    @PostMapping("/my/setting")
    public String mySetting(String password, String newPassword, RedirectAttributes redirectAttributes) throws UserPasswordException {
        userService.updateMyPass(password, newPassword);
        redirectAttributes.addFlashAttribute("successMsg", "修改成功！");
        return "redirect:/my/setting";
    }

    @GetMapping("/my/logs")
    public String myLogs(@RequestParam(value = "page", defaultValue = "1") Integer page,
                         @RequestParam(value = "size", defaultValue = "10") Integer size, Model model){
        Pageable pageable = new PageRequest(page-1, size);
        Page<UserLog> logs = userService.getMyLogs(pageable);
        model.addAttribute("records",logs);
        return "user/logs";
    }

    @GetMapping("/my/articles")
    public String myArticles() {
        return "user/articles";
    }

    @GetMapping("/my/comments")
    public String myComments() {
        return "user/comments";
    }

    @GetMapping("/my/categories")
    public String myCategories() {
        return "user/categories";
    }

    @GetMapping("/logout")
    public String logout(RedirectAttributes redirectAttributes) {
        //使用权限管理工具进行用户的退出，跳出登录，给出提示信息
        SecurityUtils.getSubject().logout();
        redirectAttributes.addFlashAttribute("message", "您已安全退出");
        return "redirect:/login";
    }

    @RequestMapping("/pages/403")
    public String unauthorizedRole() {
        log.info("------没有权限-------");
        return "pages/403";
    }


}