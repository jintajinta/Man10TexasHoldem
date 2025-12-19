create table gameLog
(
    id int unsigned auto_increment,
    startTime DATETIME null,
    endTime DATETIME null,
    gameName varchar(32) null,
    P1 varchar(16) null,
    P2 varchar(16) null,
    P3 varchar(16) null,
    P4 varchar(16) null,
    chipRate double default 0.0,
    firstChips int default 0,
    P1Chips int default 0,
    P2Chips int default 0,
    P3Chips int default 0,
    P4Chips int default 0,

    primary key(id)
);

create table handsLog
(
    id int unsigned auto_increment,
    gameId int unsigned,
    P1card varchar(16) null,
    P2card varchar(16) null,
    P3card varchar(16) null,
    P4card varchar(16) null,
    community varchar(32) null,
    foldP varchar(20) null,

    primary key(id)
);

create table playerData
(
    id int unsigned auto_increment,
    name varchar(16) null,
    uuid varchar(36) unique not null,
    totalWin int default 0,
    win int default 0,

    primary key(id)
);

create index playerData_uuid_index on playerData(uuid);

-- Sit & Go レーティングテーブル
create table sitandgo_rating
(
    id int unsigned auto_increment,
    uuid varchar(36) unique not null,
    name varchar(16) null,
    rating int default 1500,
    games_played int default 0,
    first_place int default 0,
    second_place int default 0,
    third_place int default 0,
    fourth_place int default 0,
    total_prize bigint default 0,
    last_played datetime null,
    
    primary key(id)
);

create index sitandgo_rating_uuid_index on sitandgo_rating(uuid);
create index sitandgo_rating_rating_index on sitandgo_rating(rating);

-- Sit & Go ログテーブル
create table sitandgo_log
(
    id int unsigned auto_increment,
    tournament_time datetime not null,
    buy_in bigint not null,
    multiplier double not null,
    total_pool bigint not null,
    
    -- プレイヤー情報（順位順）
    p1_uuid varchar(36) not null,
    p1_name varchar(16) not null,
    p1_prize bigint default 0,
    p1_rating_before int default 0,
    p1_rating_after int default 0,
    
    p2_uuid varchar(36) not null,
    p2_name varchar(16) not null,
    p2_prize bigint default 0,
    p2_rating_before int default 0,
    p2_rating_after int default 0,
    
    p3_uuid varchar(36) not null,
    p3_name varchar(16) not null,
    p3_prize bigint default 0,
    p3_rating_before int default 0,
    p3_rating_after int default 0,
    
    p4_uuid varchar(36) not null,
    p4_name varchar(16) not null,
    p4_prize bigint default 0,
    p4_rating_before int default 0,
    p4_rating_after int default 0,
    
    primary key(id)
);

create index sitandgo_log_time_index on sitandgo_log(tournament_time);
