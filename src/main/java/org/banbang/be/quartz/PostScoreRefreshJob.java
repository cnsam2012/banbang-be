package org.banbang.be.quartz;

import lombok.extern.slf4j.Slf4j;
import org.banbang.be.service.DiscussPostService;
import org.banbang.be.service.ElasticsearchService;
import org.banbang.be.service.LikeService;
import org.banbang.be.pojo.DiscussPost;
import org.banbang.be.util.RedisKeyUtil;
import org.banbang.be.util.constant.BbEntityType;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundSetOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 帖子分数计算刷新
 */
@Slf4j
@Component
public class PostScoreRefreshJob implements Job {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private DiscussPostService discussPostService;

    @Autowired
    private LikeService likeService;

    @Autowired
    private ElasticsearchService elasticsearchService;

    // Epoch 纪元
    private static final Date epoch;

    static {
        try {
            epoch = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2014-01-01 00:00:00");
        } catch (ParseException e) {
            throw new RuntimeException("初始化 Epoch 纪元失败", e);
        }
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        String redisKey = RedisKeyUtil.getPostScoreKey();
        BoundSetOperations operations = redisTemplate.boundSetOps(redisKey);

        if (operations.size() == 0) {
            log.info("[任务取消] 没有需要刷新的帖子");
            return ;
        }

        log.info("[任务开始] 正在刷新帖子分数: " + operations.size());
        while (operations.size() > 0) {
            this.refresh((Integer) operations.pop());
        }
        log.info("[任务结束] 帖子分数刷新完毕");
    }

    /**
     * 刷新帖子分数
     * @param postId
     */
    private void refresh(int postId) {
        DiscussPost post = discussPostService.findDiscussPostById(postId);

        if (post == null) {
            log.error("该帖子不存在: id = " + postId);
            return ;
        }

        // 是否加精
        boolean wonderful = post.getStatus() == 1;
        // 评论数量
        int commentCount = post.getCommentCount();
        // 点赞数量
        long likeCount = likeService.findEntityLikeCount(BbEntityType.ENTITY_TYPE_POST.value(), postId);

        // 计算权重
        double w = (wonderful ? 75 : 0) + commentCount * 10 + likeCount * 2;
        // 分数 = 权重 + 发帖距离天数
        double score = Math.log10(Math.max(w, 1))
                + (post.getCreateTime().getTime() - epoch.getTime()) / (1000 * 3600 * 24);
        // 更新帖子分数
        discussPostService.updateScore(postId, score);
        // 同步搜索数据
        post.setScore(score);
        elasticsearchService.saveDiscusspost(post);

    }
}
