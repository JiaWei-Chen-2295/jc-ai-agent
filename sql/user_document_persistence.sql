-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    account  varchar(256)                           not null comment '账号',
    password varchar(512)                           not null comment '密码',
    name     varchar(256)                           null comment '用户昵称',
    avatar   varchar(1024)                          null comment '用户头像',
    profile  varchar(512)                           null comment '用户简介',
    role     varchar(256) default 'user'            not null comment '用户角色：user/admin/ban',
    create_time   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete     tinyint      default 0                 not null comment '是否删除'
) comment '用户' collate = utf8mb4_unicode_ci;

-- 文档表
create table if not exists document (
    id bigint auto_increment comment 'id' primary key,
    user_id bigint not null comment '创建用户id',
    title varchar(512) not null comment '标题',
    content text not null comment '内容',
    image_url text not null comment '图片地址 Base64编码的data url',
    md5 varchar(32) not null comment 'md5',
    is_vector tinyint default 0 not null comment '是否向量化0-false 1-true',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete tinyint default 0 not null comment '是否删除'
) comment '文档';

-- 用户提交记录表
create table if not exists user_submit_record (
    id bigint auto_increment comment 'id' primary key,
    user_id bigint not null comment '用户id',
    document_id bigint not null comment '文档id',
    question varchar(1024) not null comment '问题',
    context  text not null comment '上下文',
    answer varchar(1024) not null comment '答案',
    create_time datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete tinyint default 0 not null comment '是否删除'
)
