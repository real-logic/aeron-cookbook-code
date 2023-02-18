/*
 * Copyright 2019-2023 Shaun Laurens.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aeroncookbook.cluster.rsm.node;

import com.aeroncookbook.cluster.rsm.gen.Snapshot;
import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RsmClusteredService implements ClusteredService
{
    private final RsmDemuxer demuxer;
    private final ReplicatedStateMachine stateMachine;
    private final Logger log = LoggerFactory.getLogger(RsmClusteredService.class);

    public RsmClusteredService()
    {
        stateMachine = new ReplicatedStateMachine();
        demuxer = new RsmDemuxer(stateMachine);
    }

    @Override
    public void onStart(final Cluster cluster, final Image snapshotImage)
    {
        if (snapshotImage != null)
        {
            log.info("loading snapshot");
            snapshotImage.poll(demuxer, 1);
        }
    }

    @Override
    public void onSessionOpen(final ClientSession session, final long timestamp)
    {
        log.info("Cluster Client Session opened");
    }

    @Override
    public void onSessionClose(final ClientSession session, final long timestamp, final CloseReason closeReason)
    {
        log.info("Cluster Client Session closed");
    }

    @Override
    public void onSessionMessage(
        final ClientSession session,
        final long timestamp,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final Header header)
    {
        demuxer.setSession(session);
        demuxer.onFragment(buffer, offset, length, header);
    }

    @Override
    public void onTimerEvent(final long correlationId, final long timestamp)
    {
        log.info("Cluster Node timer firing");
    }

    @Override
    public void onTakeSnapshot(final ExclusivePublication snapshotPublication)
    {
        log.info("taking snapshot");
        final ExpandableDirectByteBuffer buffer = new ExpandableDirectByteBuffer(Snapshot.BUFFER_LENGTH);

        //get the current value from the state machine
        stateMachine.takeSnapshot(buffer);

        //offer the snapshot to the publication
        snapshotPublication.offer(buffer, 0, Snapshot.BUFFER_LENGTH);
    }

    @Override
    public void onRoleChange(final Cluster.Role newRole)
    {
        log.info("Cluster Node is in role {}", newRole.name());
    }

    @Override
    public void onTerminate(final Cluster cluster)
    {
        log.info("Cluster Node is terminating");
    }
}
