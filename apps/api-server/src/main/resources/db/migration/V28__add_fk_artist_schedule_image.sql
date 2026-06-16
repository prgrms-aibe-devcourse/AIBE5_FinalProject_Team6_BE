ALTER TABLE artist_schedule_image
    ADD CONSTRAINT fk_artist_schedule_image_schedule
        FOREIGN KEY (schedule_id) REFERENCES artist_schedule (id);
