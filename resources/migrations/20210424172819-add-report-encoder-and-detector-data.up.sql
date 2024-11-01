alter table report
    add column video_encoder_status        varchar(150),
    add column extra_video_encoder_status  varchar(150),
    add column video_detector_id           varchar(150),
    add column video_detector_status       varchar(150),
    add column extra_video_detector_id     varchar(150),
    add column extra_video_detector_status varchar(150);

--;;

update report
set video_encoder_status='created'
where video_encoder_id is not null;

--;;

update report
set extra_video_encoder_status='created'
where extra_video_encoder_id is not null;
