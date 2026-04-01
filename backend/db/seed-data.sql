-- ZhiGuang demo seed data for MySQL 8+
-- Import order matters because of foreign keys:
-- users -> know_posts -> following/follower -> login_logs -> outbox

SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO users (
    id, phone, email, password_hash, nickname, avatar, bio, zg_id, gender, birthday, school, tags_json, created_at, updated_at
) VALUES
(1001, '13800000001', 'alice@example.com', NULL, '星火Alice', 'https://cdn.example.com/avatar/alice.png', '喜欢写笔记和整理知识。', 'zg1001', 'female', '2000-03-12', '同济大学', '["RAG","Spring","阅读"]', '2026-04-01 09:00:00', '2026-04-01 09:00:00'),
(1002, '13800000002', 'bob@example.com', NULL, '码上Bob', 'https://cdn.example.com/avatar/bob.png', '后端工程师，爱折腾中间件。', 'zg1002', 'male', '1999-11-08', '复旦大学', '["Kafka","MySQL","Java"]', '2026-04-01 09:05:00', '2026-04-01 09:05:00'),
(1003, '13800000003', 'cindy@example.com', NULL, 'Cindy', 'https://cdn.example.com/avatar/cindy.png', '专注产品设计和内容运营。', 'zg1003', 'female', '2001-06-21', '上海交通大学', '["产品","运营","UI"]', '2026-04-01 09:10:00', '2026-04-01 09:10:00'),
(1004, '13800000004', 'dylan@example.com', NULL, 'Dylan', 'https://cdn.example.com/avatar/dylan.png', '关注算法、检索和推荐。', 'zg1004', 'male', '1998-02-19', '浙江大学', '["算法","搜索","Embedding"]', '2026-04-01 09:15:00', '2026-04-01 09:15:00'),
(1005, '13800000005', 'eva@example.com', NULL, 'Eva', 'https://cdn.example.com/avatar/eva.png', '喜欢分享实战踩坑记录。', 'zg1005', 'female', '2000-12-30', '华东师范大学', '["实战","分享","测试"]', '2026-04-01 09:20:00', '2026-04-01 09:20:00'),
(1006, '13800000006', 'frank@example.com', NULL, 'Frank', 'https://cdn.example.com/avatar/frank.png', 'SRE 视角看系统稳定性。', 'zg1006', 'male', '1997-08-14', '同济大学', '["SRE","Linux","监控"]', '2026-04-01 09:25:00', '2026-04-01 09:25:00'),
(1007, '13800000007', 'grace@example.com', NULL, 'Grace', 'https://cdn.example.com/avatar/grace.png', '关注 AI 工具链和效率提升。', 'zg1007', 'female', '2002-01-17', '华东理工大学', '["AI工具","Prompt","效率"]', '2026-04-01 09:30:00', '2026-04-01 09:30:00'),
(1008, '13800000008', 'henry@example.com', NULL, 'Henry', 'https://cdn.example.com/avatar/henry.png', '热爱开源和工程化。', 'zg1008', 'male', '1996-05-03', '南京大学', '["开源","工程化","Git"]', '2026-04-01 09:35:00', '2026-04-01 09:35:00'),
(1009, '13800000009', 'ivy@example.com', NULL, 'Ivy', 'https://cdn.example.com/avatar/ivy.png', '做前端也做一些可视化。', 'zg1009', 'female', '2001-09-09', '东华大学', '["前端","可视化","React"]', '2026-04-01 09:40:00', '2026-04-01 09:40:00'),
(1010, '13800000010', 'jack@example.com', NULL, 'Jack', 'https://cdn.example.com/avatar/jack.png', '喜欢数据分析和表格。', 'zg1010', 'male', '1999-07-27', '华中科技大学', '["数据","分析","SQL"]', '2026-04-01 09:45:00', '2026-04-01 09:45:00'),
(1011, '13800000011', 'luna@example.com', NULL, 'Luna', 'https://cdn.example.com/avatar/luna.png', '记录学习路线和复盘。', 'zg1011', 'female', '2002-10-11', '北京航空航天大学', '["学习","复盘","成长"]', '2026-04-01 09:50:00', '2026-04-01 09:50:00'),
(1012, '13800000012', 'mike@example.com', NULL, 'Mike', 'https://cdn.example.com/avatar/mike.png', '偏爱小而美的项目。', 'zg1012', 'male', '1998-04-26', '华南理工大学', '["项目","Demo","效率"]', '2026-04-01 09:55:00', '2026-04-01 09:55:00');

INSERT INTO know_posts (
    id, tag_id, tags, title, description, content_url, content_object_key, content_etag, content_size, content_sha256,
    creator_id, is_top, type, visible, img_urls, video_url, status, create_time, update_time, publish_time
) VALUES
(3001, 11, '["Spring","Kafka","MySQL"]', 'Spring Boot 接入 Kafka 的最小可运行方案', '后端接 Kafka 的入门记录', 'https://cdn.example.com/knowposts/3001.html', 'knowposts/3001/index.html', 'etag-3001', 18452, '1111111111111111111111111111111111111111111111111111111111111111', 1002, 1, 'image_text', 'public', '["https://cdn.example.com/knowposts/3001-1.png"]', NULL, 'published', '2026-04-01 10:00:00', '2026-04-01 10:00:00', '2026-04-01 10:05:00'),
(3002, 12, '["RAG","Embedding","ElasticSearch"]', 'RAG 项目里向量检索的几个坑', '向量检索调试记录', 'https://cdn.example.com/knowposts/3002.html', 'knowposts/3002/index.html', 'etag-3002', 22310, '2222222222222222222222222222222222222222222222222222222222222222', 1001, 0, 'image_text', 'public', '["https://cdn.example.com/knowposts/3002-1.png","https://cdn.example.com/knowposts/3002-2.png"]', NULL, 'published', '2026-04-01 10:10:00', '2026-04-01 10:10:00', '2026-04-01 10:16:00'),
(3003, 13, '["UI","Design","React"]', '一个更顺手的前端信息流布局', '适合内容型应用的信息流卡片', 'https://cdn.example.com/knowposts/3003.html', 'knowposts/3003/index.html', 'etag-3003', 15880, '3333333333333333333333333333333333333333333333333333333333333333', 1003, 0, 'image_text', 'public', '["https://cdn.example.com/knowposts/3003-1.png"]', NULL, 'published', '2026-04-01 10:20:00', '2026-04-01 10:20:00', '2026-04-01 10:23:00'),
(3004, 11, '["Redis","Lua","Counter"]', '计数缓存和 Redis Lua 的组合实践', '适合高并发计数的实现方式', 'https://cdn.example.com/knowposts/3004.html', 'knowposts/3004/index.html', 'etag-3004', 16640, '4444444444444444444444444444444444444444444444444444444444444444', 1004, 1, 'image_text', 'public', '["https://cdn.example.com/knowposts/3004-1.png"]', NULL, 'published', '2026-04-01 10:30:00', '2026-04-01 10:30:00', '2026-04-01 10:35:00'),
(3005, 14, '["Git","OpenSource","Workflow"]', '团队协作里最容易踩的 Git 坑', '一些很实用的协作习惯', 'https://cdn.example.com/knowposts/3005.html', 'knowposts/3005/index.html', 'etag-3005', 13220, '5555555555555555555555555555555555555555555555555555555555555555', 1005, 0, 'image_text', 'public', '["https://cdn.example.com/knowposts/3005-1.png"]', NULL, 'published', '2026-04-01 10:40:00', '2026-04-01 10:40:00', '2026-04-01 10:43:00'),
(3006, 15, '["SRE","Monitor","Alert"]', '服务稳定性排查清单', '上线后排障的几个动作', 'https://cdn.example.com/knowposts/3006.html', 'knowposts/3006/index.html', 'etag-3006', 14110, '6666666666666666666666666666666666666666666666666666666666666666', 1006, 0, 'image_text', 'private', '["https://cdn.example.com/knowposts/3006-1.png"]', NULL, 'draft', '2026-04-01 10:50:00', '2026-04-01 10:50:00', NULL),
(3007, 12, '["AI工具","Prompt","效率"]', '把日常写作交给 AI 的工作流', '适合知识整理和摘要', 'https://cdn.example.com/knowposts/3007.html', 'knowposts/3007/index.html', 'etag-3007', 20100, '7777777777777777777777777777777777777777777777777777777777777777', 1007, 0, 'image_text', 'public', '["https://cdn.example.com/knowposts/3007-1.png"]', NULL, 'published', '2026-04-01 11:00:00', '2026-04-01 11:00:00', '2026-04-01 11:06:00'),
(3008, 13, '["前端","可视化","React"]', '做一个更顺眼的数据面板', '信息密度和层次感的平衡', 'https://cdn.example.com/knowposts/3008.html', 'knowposts/3008/index.html', 'etag-3008', 17330, '8888888888888888888888888888888888888888888888888888888888888888', 1009, 0, 'image_text', 'public', '["https://cdn.example.com/knowposts/3008-1.png","https://cdn.example.com/knowposts/3008-2.png"]', NULL, 'published', '2026-04-01 11:10:00', '2026-04-01 11:10:00', '2026-04-01 11:15:00'),
(3009, 11, '["SQL","MySQL","数据"]', 'MySQL 批量插入数据的注意点', '批量导入时的几个细节', 'https://cdn.example.com/knowposts/3009.html', 'knowposts/3009/index.html', 'etag-3009', 11450, '9999999999999999999999999999999999999999999999999999999999999999', 1010, 0, 'image_text', 'public', '["https://cdn.example.com/knowposts/3009-1.png"]', NULL, 'published', '2026-04-01 11:20:00', '2026-04-01 11:20:00', '2026-04-01 11:23:00'),
(3010, 14, '["学习","复盘","成长"]', '如何把每天的学习变成可复用笔记', '一篇偏方法论的小结', 'https://cdn.example.com/knowposts/3010.html', 'knowposts/3010/index.html', 'etag-3010', 12660, 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 1011, 1, 'image_text', 'public', '["https://cdn.example.com/knowposts/3010-1.png"]', NULL, 'published', '2026-04-01 11:30:00', '2026-04-01 11:30:00', '2026-04-01 11:35:00'),
(3011, 13, '["项目","Demo","效率"]', '小项目也要有完整闭环', '从需求到上线的节奏', 'https://cdn.example.com/knowposts/3011.html', 'knowposts/3011/index.html', 'etag-3011', 11920, 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 1012, 0, 'image_text', 'public', '["https://cdn.example.com/knowposts/3011-1.png"]', NULL, 'published', '2026-04-01 11:40:00', '2026-04-01 11:40:00', '2026-04-01 11:45:00'),
(3012, 15, '["系统设计","稳定性","Kafka"]', '一个服务为什么要有降级策略', '从用户体验看稳定性设计', 'https://cdn.example.com/knowposts/3012.html', 'knowposts/3012/index.html', 'etag-3012', 14890, 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', 1006, 0, 'video', 'public', NULL, 'https://cdn.example.com/videos/3012.mp4', 'deleted', '2026-04-01 11:50:00', '2026-04-01 11:50:00', '2026-04-01 11:58:00');

INSERT INTO following (
    id, from_user_id, to_user_id, rel_status, created_at, updated_at
) VALUES
(4001, 1001, 1002, 1, '2026-04-01 12:00:00.000', '2026-04-01 12:00:00.000'),
(4002, 1001, 1004, 1, '2026-04-01 12:01:00.000', '2026-04-01 12:01:00.000'),
(4003, 1001, 1007, 1, '2026-04-01 12:02:00.000', '2026-04-01 12:02:00.000'),
(4004, 1002, 1004, 1, '2026-04-01 12:03:00.000', '2026-04-01 12:03:00.000'),
(4005, 1002, 1005, 1, '2026-04-01 12:04:00.000', '2026-04-01 12:04:00.000'),
(4006, 1003, 1001, 1, '2026-04-01 12:05:00.000', '2026-04-01 12:05:00.000'),
(4007, 1003, 1008, 1, '2026-04-01 12:06:00.000', '2026-04-01 12:06:00.000'),
(4008, 1004, 1002, 1, '2026-04-01 12:07:00.000', '2026-04-01 12:07:00.000'),
(4009, 1004, 1006, 1, '2026-04-01 12:08:00.000', '2026-04-01 12:08:00.000'),
(4010, 1005, 1009, 1, '2026-04-01 12:09:00.000', '2026-04-01 12:09:00.000'),
(4011, 1006, 1001, 1, '2026-04-01 12:10:00.000', '2026-04-01 12:10:00.000'),
(4012, 1007, 1003, 1, '2026-04-01 12:11:00.000', '2026-04-01 12:11:00.000'),
(4013, 1008, 1010, 1, '2026-04-01 12:12:00.000', '2026-04-01 12:12:00.000'),
(4014, 1009, 1004, 1, '2026-04-01 12:13:00.000', '2026-04-01 12:13:00.000'),
(4015, 1010, 1011, 1, '2026-04-01 12:14:00.000', '2026-04-01 12:14:00.000');

INSERT INTO follower (
    id, to_user_id, from_user_id, rel_status, created_at, updated_at
) VALUES
(5001, 1002, 1001, 1, '2026-04-01 12:00:00.000', '2026-04-01 12:00:00.000'),
(5002, 1004, 1001, 1, '2026-04-01 12:01:00.000', '2026-04-01 12:01:00.000'),
(5003, 1007, 1001, 1, '2026-04-01 12:02:00.000', '2026-04-01 12:02:00.000'),
(5004, 1004, 1002, 1, '2026-04-01 12:03:00.000', '2026-04-01 12:03:00.000'),
(5005, 1005, 1002, 1, '2026-04-01 12:04:00.000', '2026-04-01 12:04:00.000'),
(5006, 1001, 1003, 1, '2026-04-01 12:05:00.000', '2026-04-01 12:05:00.000'),
(5007, 1008, 1003, 1, '2026-04-01 12:06:00.000', '2026-04-01 12:06:00.000'),
(5008, 1002, 1004, 1, '2026-04-01 12:07:00.000', '2026-04-01 12:07:00.000'),
(5009, 1006, 1004, 1, '2026-04-01 12:08:00.000', '2026-04-01 12:08:00.000'),
(5010, 1009, 1005, 1, '2026-04-01 12:09:00.000', '2026-04-01 12:09:00.000'),
(5011, 1001, 1006, 1, '2026-04-01 12:10:00.000', '2026-04-01 12:10:00.000'),
(5012, 1003, 1007, 1, '2026-04-01 12:11:00.000', '2026-04-01 12:11:00.000'),
(5013, 1010, 1008, 1, '2026-04-01 12:12:00.000', '2026-04-01 12:12:00.000'),
(5014, 1004, 1009, 1, '2026-04-01 12:13:00.000', '2026-04-01 12:13:00.000'),
(5015, 1011, 1010, 1, '2026-04-01 12:14:00.000', '2026-04-01 12:14:00.000');

INSERT INTO login_logs (
    id, user_id, identifier, channel, ip, user_agent, status, created_at
) VALUES
(6001, 1001, '13800000001', 'CODE', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0', 'SUCCESS', '2026-04-01 13:00:00'),
(6002, 1002, '13800000002', 'PASSWORD', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0', 'SUCCESS', '2026-04-01 13:02:00'),
(6003, 1003, 'cindy@example.com', 'CODE', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0', 'SUCCESS', '2026-04-01 13:04:00'),
(6004, 1004, '13800000004', 'PASSWORD', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0', 'FAILED', '2026-04-01 13:06:00'),
(6005, 1004, '13800000004', 'CODE', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0', 'SUCCESS', '2026-04-01 13:08:00'),
(6006, 1005, '13800000005', 'PASSWORD', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0', 'SUCCESS', '2026-04-01 13:10:00'),
(6007, 1006, '13800000006', 'CODE', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0', 'SUCCESS', '2026-04-01 13:12:00'),
(6008, 1007, '13800000007', 'CODE', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0', 'SUCCESS', '2026-04-01 13:14:00'),
(6009, 1008, '13800000008', 'PASSWORD', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0', 'SUCCESS', '2026-04-01 13:16:00'),
(6010, 1009, '13800000009', 'CODE', '127.0.0.1', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0', 'FAILED', '2026-04-01 13:18:00');

INSERT INTO outbox (
    id, aggregate_type, aggregate_id, type, payload, created_at
) VALUES
(7001, 'following', 4001, 'FollowCreated', '{"type":"FollowCreated","fromUserId":1001,"toUserId":1002,"id":4001}', '2026-04-01 12:00:00.000'),
(7002, 'following', 4002, 'FollowCreated', '{"type":"FollowCreated","fromUserId":1001,"toUserId":1004,"id":4002}', '2026-04-01 12:01:00.000'),
(7003, 'following', 4004, 'FollowCreated', '{"type":"FollowCreated","fromUserId":1002,"toUserId":1004,"id":4004}', '2026-04-01 12:03:00.000'),
(7004, 'following', 4008, 'FollowCreated', '{"type":"FollowCreated","fromUserId":1004,"toUserId":1002,"id":4008}', '2026-04-01 12:07:00.000'),
(7005, 'knowpost', 3001, 'Publish', '{"entity":"knowpost","op":"insert","id":3001,"creatorId":1002}', '2026-04-01 10:05:00.000'),
(7006, 'knowpost', 3002, 'Publish', '{"entity":"knowpost","op":"insert","id":3002,"creatorId":1001}', '2026-04-01 10:16:00.000'),
(7007, 'knowpost', 3004, 'Publish', '{"entity":"knowpost","op":"insert","id":3004,"creatorId":1004}', '2026-04-01 10:35:00.000'),
(7008, 'knowpost', 3012, 'Delete', '{"entity":"knowpost","op":"delete","id":3012,"creatorId":1006}', '2026-04-01 11:58:00.000');

COMMIT;

