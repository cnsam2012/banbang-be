package org.banbang.be.controller.view;

import io.swagger.annotations.Api;
import org.banbang.be.pojo.DiscussPost;
import org.banbang.be.pojo.Page;
import org.banbang.be.pojo.User;
import org.banbang.be.service.DiscussPostService;
import org.banbang.be.service.LikeService;
import org.banbang.be.service.UserService;
import org.banbang.be.util.constant.BbEntityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import springfox.documentation.annotations.ApiIgnore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页
 */
@Controller
@Api(tags = "首页")
@ApiIgnore
public class IndexController {

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private UserService userService;

    @Autowired
    private LikeService likeService;

    @GetMapping("/")
    public String root() {
        return "forward:/index";
    }

    /**
     * 进入首页
     * @param model
     * @param page
     * @param orderMode 默认是 0（最新）
     * @return
     */
    @GetMapping("index")
    public String getIndexPage(
            Model model,
            Page page,
            @RequestParam(name = "orderMode", defaultValue = "0") int orderMode
    ) {
        // 获取总页数
        page.setRows(discussPostService.findDiscussPostRows(0));
        page.setPath("/index?orderMode=" + orderMode);

        // 分页查询
        List<DiscussPost> list = discussPostService.findDiscussPosts(0, page.getOffset(), page.getLimit(), orderMode);

        // 封装帖子和该帖子对应的用户信息
        List<Map<String, Object>> discussPosts = new ArrayList<>();
        if (list != null) {
            for (DiscussPost post : list) {
                Map<String, Object> map = new HashMap<>();
                map.put("post", post);

                User user = userService.findUserById(post.getUserId());
                map.put("user", user);

                long likeCount = likeService.findEntityLikeCount(BbEntityType.ENTITY_TYPE_POST.value(), post.getId());
                map.put("likeCount", likeCount);

                discussPosts.add(map);
            }
        }
        model.addAttribute("discussPosts", discussPosts);
        model.addAttribute("orderMode", orderMode);
        model.addAttribute("page", page);

        return "index";
    }



    /**
     * 进入 500 错误界面
     * @return
     */
    @GetMapping("error")
    public String getErrorPage() {
        return "error/500";
    }


    /**
     * 没有权限访问时的错误界面（也是 404）
     * @return
     */
    @GetMapping("denied")
    public String getDeniedPage() {
        return "error/404";
    }

}
