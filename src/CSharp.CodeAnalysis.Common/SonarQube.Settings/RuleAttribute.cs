﻿/*
 * SonarQube C# Code Analysis
 * Copyright (C) 2015 SonarSource
 * sonarqube@googlegroups.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */

using System;

namespace SonarQube.CSharp.CodeAnalysis.Common
{
    [AttributeUsage(AttributeTargets.Class)]
    public sealed class RuleAttribute : Attribute
    {
        public string Key { get; private set; }
        public string Title { get; private set; }
        public Severity Severity { get; private set; }
        public bool IsActivatedByDefault { get; private set; }

        public RuleAttribute(string key, Severity severity, string title, bool isActivatedByDefault)
        {
            Key = key;
            Title = title;
            Severity = severity;
            IsActivatedByDefault = isActivatedByDefault;
        }
    }
}