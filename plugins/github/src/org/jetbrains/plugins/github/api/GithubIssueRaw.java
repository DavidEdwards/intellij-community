/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings({"UnusedDeclaration", "ConstantConditions"})
class GithubIssueRaw implements DataConstructor<GithubIssue> {
  @Nullable public String url;
  @Nullable public String htmlUrl;
  @Nullable public Long number;
  @Nullable public String state;
  @Nullable public String title;
  @Nullable public String body;

  @Nullable public GithubUserRaw user;
  @Nullable public GithubUserRaw assignee;

  @Nullable public Date closedAt;
  @Nullable public Date createdAt;
  @Nullable public Date updatedAt;

  @NotNull
  @Override
  public GithubIssue create() {
    GithubUser assignee = this.assignee == null ? null : this.assignee.create();
    return new GithubIssue(htmlUrl, number, state, title, body, user.create(), assignee, closedAt, createdAt, updatedAt);
  }
}
