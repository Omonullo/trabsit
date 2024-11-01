CREATE OR REPLACE FUNCTION clear_notify_if_status_changed() RETURNS trigger as
$BODY$
BEGIN
    IF (NEW.status <> OLD.status) THEN
        NEW.creator_client_notified_at := null;
    END IF;
    RETURN NEW;
END;
$BODY$
    LANGUAGE plpgsql;

--;;

CREATE TRIGGER offense_status_trigger
    BEFORE UPDATE
    ON offense
    FOR EACH ROW
EXECUTE PROCEDURE clear_notify_if_status_changed();

--;;

CREATE TRIGGER report_status_trigger
    BEFORE UPDATE
    ON report
    FOR EACH ROW
EXECUTE PROCEDURE clear_notify_if_status_changed();
