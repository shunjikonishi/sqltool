task :default => "schedule"

task "schedule" do
	sh "target/start -Dsqltool.mode=schedule -Duser.timezone=" + ENV['TIMEZONE']
end
