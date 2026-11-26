WITH access AS (
    SELECT request_verb AS method, TRY(url_extract_path(request_url)) AS path,
           elb_status_code AS status,
           CASE WHEN TRY_CAST(target_status_code AS integer) = elb_status_code
                THEN 'BACKEND' ELSE 'ALB' END AS source,
           IF(target_processing_time >= 0, target_processing_time * 1000, NULL) AS duration,
           TRY(from_iso8601_timestamp(time)) AS seen
    FROM btb_logs.alb_access_logs
    WHERE day BETWEEN '{firstDay}' AND '{lastDay}'
      AND elb = '{alb}'
), routes AS (
    SELECT method, {routeCase} AS route, status, source, duration, seen
    FROM access
    WHERE starts_with(path, '/api/') AND seen >= from_iso8601_timestamp('{from}')
      AND seen < from_iso8601_timestamp('{to}')
), counts AS (
    SELECT method, route, status, source, grouping(status) AS group_level,
           count(*) AS requests, avg(duration) AS average_ms,
           approx_percentile(duration, 0.95) AS p95_ms, max(seen) AS last_seen
    FROM routes
    GROUP BY GROUPING SETS ((method, route), (method, route, status, source))
), ranked AS (
    SELECT *, row_number() OVER (PARTITION BY group_level, status / 100 ORDER BY
        IF(group_level = 0, requests, NULL) DESC NULLS LAST,
        p95_ms DESC NULLS LAST, method, route, status, source) AS rank_no
    FROM counts
    WHERE (group_level = 1 AND p95_ms IS NOT NULL) OR status BETWEEN 400 AND 599
)
SELECT method, route, status, source, requests, average_ms, p95_ms, to_iso8601(last_seen), group_level
FROM ranked WHERE rank_no <= 30 ORDER BY group_level, rank_no
