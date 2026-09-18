#!/usr/bin/env ruby
# Exercise build/publication guards with an isolated DSL. Never loads Fastlane or calls a network.
require 'tmpdir'
require 'fileutils'

module CredentialsManager
  module AppfileConfig
    def self.try_fetch_value(_key) = nil
  end
end

module SharedValues
  GRADLE_APK_OUTPUT_PATH = :apk
  GRADLE_AAB_OUTPUT_PATH = :aab
end

module UI
  def self.user_error!(message) = raise(message)
end

class LaneHarness
  attr_reader :lanes, :lane_context, :gradle_options, :remote_calls
  def initialize
    @lanes, @lane_context, @remote_calls = {}, {}, []
  end
  def lane(name, &block) = @lanes[name] = block
  def desc(_text); end
  def fastlane_version(_version); end
  def opt_out_usage; end
  def platform(_name) = yield
  def puts(*_args); end
  def android_get_version_name = 'test'
  def android_get_version_code = 87
  def get_changelog(**_args) = 'test changes'
  def last_git_commit = { commit_hash: 'a' * 40 }
  def set_github_release(**args) = @remote_calls << args
  def gradle(**args)
    @gradle_options = args
    key = args[:task] == 'assemble' ? :apk : :aab
    path = File.expand_path("../test-output.#{key}")
    File.write(path, 'fake artifact; never an APK')
    @lane_context[key] = path
    'stubbed build'
  end
  def method_missing(name, *args, **kwargs)
    return @lanes.fetch(name).call(kwargs.empty? ? (args.first || {}) : kwargs) if @lanes.key?(name)
    raise "Unexpected action: #{name}"
  end
  def respond_to_missing?(name, _private = false) = @lanes.key?(name)
end

def assert(condition, message)
  raise message unless condition
end

def rejects(message)
  begin
    yield
  rescue RuntimeError => e
    raise unless e.message.include?(message)
    return
  end
  raise "Expected rejection: #{message}"
end

source = File.read(File.expand_path('../fastlane/Fastfile', __dir__))
%w[KEYSTORE_PATH KEYSTORE_PASSWORD KEYSTORE_ALIAS KEYSTORE_ALIAS_PASSWORD].each { |key| ENV.delete(key) }
Dir.mktmpdir('enforcedoze-lanes-') do |directory|
  FileUtils.mkdir_p(File.join(directory, 'fastlane'))
  Dir.chdir(File.join(directory, 'fastlane')) do
    harness = LaneHarness.new
    harness.instance_eval(source, 'fastlane/Fastfile')
    harness.build
    assert(harness.gradle_options[:task] == 'assemble' && harness.gradle_options[:properties].empty?, 'Default build must be unsigned APK')
    harness.build(flavor: 'beta')
    assert(harness.gradle_options[:task] == 'bundle', 'Store build must produce AAB')
    rejects('Publication requires') { harness.build(options: { publish: true }) }
    ENV['KEYSTORE_PATH'] = '/private/test.jks'
    rejects('all four') { harness.build }
    ENV['KEYSTORE_PASSWORD'] = ENV['KEYSTORE_ALIAS_PASSWORD'] = 'test-only'
    ENV['KEYSTORE_ALIAS'] = 'test'
    harness.build
    assert(harness.gradle_options[:print_command] == false && harness.gradle_options[:properties]['android.injected.signing.store.file'] == '/private/test.jks', 'Signing must preserve absolute paths and hide command')
    harness.build_flavor
    assert(harness.remote_calls.empty?, 'Default lane must never publish')
    rejects('create_tag:true') { harness.build_flavor(options: { publish: true }) }
    harness.build_flavor(options: { publish: true, create_tag: true })
    release = harness.remote_calls.fetch(0)
    assert(release[:commitish] == 'a' * 40 && release[:upload_assets].size == 1 && File.file?(release[:upload_assets][0]), 'Explicit release must use built artifact and intended commit')
  end
end
puts 'PASS: 8 isolated Fastlane guard/configuration checks; no build or remote action executed.'
