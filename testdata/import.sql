-- /Query/てすと
/*
てすとのSQL
の説明
*/

select * from score_table

-- /Query/ランダムな10人の得点
select first_name || ' ' || last_name as name,
       math,
       english,
       japanese
  from score_table
 order by random()
 limit 10

-- /LineGraph/グラフ/ランダムな100人の折れ線グラフ
/*
得点分布
*/

select * from (
select first_name || ' ' || last_name as name,
       math,
       english,
       japanese
  from score_table
 order by random()
 limit 100
) rand order by 2

-- /BarGraph/グラフ/ランダムな10人の得点グラフ
select first_name || ' ' || last_name as name,
       math,
       english,
       japanese
  from score_table
 order by random()
 limit 10

-- /PieGraph/グラフ/数学の点数分布
-- {"others":{"count":"12","label":"その他"}}
select '100', count(*) from score_table where math = 100
union
select '90 - 100', count(*) from score_table where math >= 90 and math < 100
union
select '80 - 90', count(*) from score_table where math >= 80 and math < 90
union
select '70 - 80', count(*) from score_table where math >= 70 and math < 80
union
select '60 - 70', count(*) from score_table where math >= 60 and math < 70
union
select '50 - 60', count(*) from score_table where math >= 50 and math < 60
union
select '40 - 50', count(*) from score_table where math >= 40 and math < 50
union
select '30 - 40', count(*) from score_table where math >= 30 and math < 40
union
select '20 - 30', count(*) from score_table where math >= 20 and math < 30
union
select '10 - 20', count(*) from score_table where math >= 10 and math < 20
union
select '0 - 10', count(*) from score_table where math >= 0 and math < 10
order by 2 desc

-- /LineGraph/グラフ/誕生月別平均点
SELECT extract('month' from birthday) as Month,
       AVG(math) as Math,
       AVG(english) as English,
       AVG(japanese) as Japanese
  FROM score_table
 GROUP BY 1
 ORDER BY 1

-- /Query/パラメータ/誕生月別一覧
/*
誕生月を入力してください。
*/

select * from score_table
 where extract('month' from birthday) = :誕生月:int
 order by birthday

-- /Schedule/スナップショット/ランダムな一人
-- {"spreadsheet":"Furoku","worksheet":"ランダム","time":"01:00:00"}
select first_name, last_name, math, english, japanese from score_table
 order by random() limit 1

