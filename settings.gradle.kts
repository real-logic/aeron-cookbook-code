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

rootProject.name = "aeron-cookbook"

include ("sbe-core", "sbe-protocol", "archive-core", "ipc-core", "aeron-core", "cluster-core", "theory", "agrona", "archive-multi-host:archive-host", "archive-multi-host:archive-client", "archive-replication:archive-client", "archive-replication:archive-host", "archive-replication:archive-backup", "archive-replication:common", "aeron-mdc:aeron-mdc-publisher", "aeron-mdc:aeron-mdc-subscriber")
