package vn.edu.toeic.client.monitoring.harness;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.toeic.client.monitoring.trace.TraceReader;

class ControlledProcessHarnessTest {
    @TempDir Path directory;
    @Test void realChildrenCreateGroundTruthEvenWhenCollectorAlwaysReportsEmpty()throws Exception {
        var engine=new ControlledProcessHarness(HarnessFixtures.config(),List::of,ControlledProcessHarness::launchChild);
        var result=engine.run(directory.resolve("normal"));assertThat(result.status()).isEqualTo("COMPLETE");assertThat(result.resourcesCleaned()).isTrue();
        var truth=HarnessArtifacts.readGroundTruth(directory.resolve("normal/ground-truth.jsonl"));
        assertThat(truth.trials()).hasSize(3).allMatch(t -> t.pid()!=null&&t.status().equals("EXITED")&&t.exitCode()==0);
        assertThat(truth.trials()).allMatch(t -> t.launchBeginElapsedNanos()>=t.planned().plannedLaunchElapsedNanos());
        assertThat(truth.trials()).allMatch(t -> t.goSentElapsedNanos()>t.readyReceivedElapsedNanos()&&t.exitObservedElapsedNanos()>t.goSentElapsedNanos());
        assertThat(result.join().observed()).isZero();assertThat(result.join().notObserved()).isEqualTo(3);assertThat(HarnessArtifacts.verifyManifest(directory.resolve("normal"))).isTrue();
        assertThat(TraceReader.read(directory.resolve("normal/observations.jsonl")).samples()).allMatch(s -> s.processes().isEmpty());
        for(var trial:truth.trials())assertThat(ProcessHandle.of(trial.pid()).map(ProcessHandle::isAlive).orElse(false)).isFalse();
        assertWorkersGone();
    }
    @ParameterizedTest @ValueSource(strings={"FAIL","NO_READY","HANG"})
    void childFailureNoReadyAndExitTimeoutAreIncompleteAndCleanOwnedProcesses(String mode)throws Exception {
        var started=new java.util.concurrent.CopyOnWriteArrayList<Process>();
        var engine=new ControlledProcessHarness(HarnessFixtures.config(),List::of,(run,trial) -> {Process child=fixture(mode,run,trial);started.add(child);return child;});
        var result=engine.run(directory.resolve(mode));assertThat(result.status()).isEqualTo("INCOMPLETE");assertThat(result.resourcesCleaned()).isTrue();
        assertThat(result.join().notObserved()).isZero();assertThat(result.join().inconclusive()).isEqualTo(3);
        assertThat(started).isNotEmpty().noneMatch(Process::isAlive);
        var truth=HarnessArtifacts.readGroundTruth(directory.resolve(mode+"/ground-truth.jsonl"));
        String expected=mode.equals("NO_READY")?"READY_TIMEOUT":mode.equals("HANG")?"EXIT_TIMEOUT":"CHILD_ERROR";
        assertThat(truth.trials()).anyMatch(t -> t.status().equals(expected));assertThat(HarnessArtifacts.verifyManifest(directory.resolve(mode))).isFalse();assertWorkersGone();
    }
    @Test void launchFailurePreservesPlanAndNeverInventsPidOrMiss()throws Exception {
        var engine=new ControlledProcessHarness(HarnessFixtures.config(),List::of,(run,trial) -> {throw new java.io.IOException("MOCK private path");});
        var result=engine.run(directory.resolve("launchfail"));assertThat(result.status()).isEqualTo("INCOMPLETE");assertThat(result.join().notObserved()).isZero();
        var truth=HarnessArtifacts.readGroundTruth(directory.resolve("launchfail/ground-truth.jsonl"));assertThat(truth.trials()).allMatch(t -> t.pid()==null);assertThat(truth.trials()).anyMatch(t -> t.status().equals("LAUNCH_FAILED"));assertWorkersGone();
    }
    @Test void cancellationWhileAwaitingReadyTerminatesChildAndPreservesIncompleteArtifacts()throws Exception {
        var entered=new CountDownLatch(1);var child=new AtomicReference<Process>();var result=new AtomicReference<ControlledProcessHarness.Result>();var failure=new AtomicReference<Throwable>();
        var engine=new ControlledProcessHarness(HarnessFixtures.config(),List::of,(run,trial) -> {Process process=fixture("NO_READY",run,trial);child.set(process);entered.countDown();return process;});
        var thread=new Thread(() -> {try{result.set(engine.run(directory.resolve("cancel")));}catch(Throwable problem){failure.set(problem);}},"test-harness-owner");
        thread.start();try{assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();engine.cancel();thread.join(10000);
            assertThat(thread.isAlive()).isFalse();assertThat(failure.get()).isNull();assertThat(result.get().status()).isEqualTo("INCOMPLETE");assertThat(child.get().isAlive()).isFalse();assertThat(result.get().resourcesCleaned()).isTrue();assertWorkersGone();
        }finally{engine.cancel();thread.join(10000);}
    }
    @Test void sourceFailureStopsRunAndExistingOutputIsPreserved()throws Exception {
        Path existing=directory.resolve("existing");Files.createDirectory(existing);Files.writeString(existing.resolve("keep.txt"),"original");
        var engine=new ControlledProcessHarness(HarnessFixtures.config(),() -> {throw new IllegalStateException("MOCK source");},ControlledProcessHarness::launchChild);
        assertThatThrownBy(() -> engine.run(existing)).isInstanceOf(java.io.IOException.class);assertThat(Files.readString(existing.resolve("keep.txt"))).isEqualTo("original");
        var another=new ControlledProcessHarness(HarnessFixtures.config(),() -> {throw new IllegalStateException("MOCK source");},ControlledProcessHarness::launchChild);
        var result=another.run(directory.resolve("sourcefail"));assertThat(result.status()).isEqualTo("INCOMPLETE");assertThat(result.error()).isEqualTo("SOURCE_FAILURE");assertThat(result.join().notObserved()).isZero();assertWorkersGone();
    }
    @Test void knownOwnedDescendantIsCleanedEvenAfterItsRootExits()throws Exception {
        var pids=new java.util.concurrent.CopyOnWriteArrayList<Path>();
        var engine=new ControlledProcessHarness(HarnessFixtures.config(),List::of,(run,trial) -> {
            Path pidFile=directory.resolve(trial.trialId()+".pid");pids.add(pidFile);
            String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
            String cp=Path.of(ChildFailureFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            return new ProcessBuilder(java,"-cp",cp,ChildFailureFixture.class.getName(),"TREE",run,trial.trialId(),Integer.toString(trial.targetMillis()),pidFile.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        });
        var result=engine.run(directory.resolve("tree"));assertThat(result.status()).isEqualTo("COMPLETE");assertThat(result.resourcesCleaned()).isTrue();
        assertThat(pids).hasSize(3);for(var file:pids)assertThat(ProcessHandle.of(Long.parseLong(Files.readString(file))).map(ProcessHandle::isAlive).orElse(false)).isFalse();assertWorkersGone();
    }
    static Process fixture(String mode,String run,HarnessPlan.Trial trial)throws Exception {
        String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        String classpath=Path.of(ChildFailureFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        return new ProcessBuilder(java,"-cp",classpath,ChildFailureFixture.class.getName(),mode,run,trial.trialId(),Integer.toString(trial.targetMillis())).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }
    static void assertWorkersGone()throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(System.nanoTime()<end){if(Thread.getAllStackTraces().keySet().stream().noneMatch(t -> t.isAlive()&&(t.getName().startsWith("toeic-harness-")||t.getName().equals("toeic-process-collector")||t.getName().equals("toeic-trace-writer"))))return;Thread.sleep(20);}
        fail("Owned harness/collector/trace worker remains alive");
    }
}
