task :default => "schedule"

task "schedule" do
	opt = ''
	timezone = ENV['TIMEZONE']
	if (timezone != nil)
		opt = "-Duser.timezone=" + timezone
	end
	sh "target/start -Dsqltool.mode=schedule " + opt
end

task "setup" do
	sh "target/start -Dsqltool.mode=setup"
end
